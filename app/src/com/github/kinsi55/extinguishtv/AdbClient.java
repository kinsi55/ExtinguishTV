package com.github.kinsi55.extinguishtv;

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Minimal client for the classic (pre-TLS) ADB wire protocol, enough to authenticate
 * against a TV's adbd over the network and run shell/reboot services as the shell user.
 */
final class AdbClient implements Closeable {
    private static final int CNXN = 0x4e584e43;
    private static final int AUTH = 0x48545541;
    private static final int OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f;
    private static final int CLSE = 0x45534c43;
    private static final int WRTE = 0x45545257;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final int VERSION = 0x01000000;
    private static final int MAX_DATA = 4096;

    // ASN.1 DigestInfo prefix for SHA-1; adbd verifies the token as if it were a SHA-1 digest.
    private static final byte[] SHA1_DIGEST_INFO = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    private static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextLocalId = 1;

    /** Connects and authenticates; blocks up to authTimeoutMs while the user answers the on-screen prompt. */
    AdbClient(String host, int port, KeyPair keys, String keyName, int authTimeoutMs)
            throws IOException, GeneralSecurityException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5_000);
        socket.setSoTimeout(authTimeoutMs);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new BufferedOutputStream(socket.getOutputStream());

        send(CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
        boolean sentSignature = false, sentPublicKey = false;
        while (true) {
            Message m = read();
            if (m.command == CNXN) break;
            if (m.command != AUTH || m.arg0 != AUTH_TOKEN) {
                throw new IOException("Unexpected ADB message 0x" + Integer.toHexString(m.command));
            }
            if (!sentSignature) {
                send(AUTH, AUTH_SIGNATURE, 0, sign(keys.getPrivate(), m.data));
                sentSignature = true;
            } else if (!sentPublicKey) {
                send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyPayload((RSAPublicKey) keys.getPublic(), keyName));
                sentPublicKey = true;
            } else {
                throw new IOException("ADB authorization was rejected");
            }
        }
        socket.setSoTimeout(60_000);
    }

    /** Runs an ADB service such as "shell:pm list packages" and returns its output. */
    String run(String service) throws IOException {
        int localId = nextLocalId++;
        int remoteId = 0;
        send(OPEN, localId, 0, (service + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            Message m;
            try {
                m = read();
            } catch (EOFException e) {
                // "reboot:" drops the connection instead of closing the stream.
                if (service.startsWith("reboot")) break;
                throw e;
            }
            if (m.arg1 != localId) continue;
            if (m.command == OKAY) {
                remoteId = m.arg0;
            } else if (m.command == WRTE) {
                output.write(m.data);
                send(OKAY, localId, m.arg0, new byte[0]);
            } else if (m.command == CLSE) {
                if (remoteId != 0) send(CLSE, localId, remoteId, new byte[0]);
                break;
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private Message read() throws IOException {
        byte[] header = new byte[24];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        m.data = new byte[b.getInt()];
        in.readFully(m.data);
        return m;
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int checksum = 0;
        for (byte x : data) checksum += x & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(checksum).putInt(~command);
        out.write(b.array());
        out.write(data);
        out.flush();
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws GeneralSecurityException {
        Signature s = Signature.getInstance("NONEwithRSA");
        s.initSign(key);
        s.update(SHA1_DIGEST_INFO);
        s.update(token);
        return s.sign();
    }

    /** Encodes the key in Android's mincrypt RSAPublicKey layout, base64'd, as adbd expects. */
    private static byte[] publicKeyPayload(RSAPublicKey key, String name) {
        int bytes = 256; // 2048-bit modulus
        BigInteger n = key.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(bytes * 8 * 2).mod(n);

        ByteBuffer b = ByteBuffer.allocate(4 + 4 + bytes + bytes + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(bytes / 4);
        b.putInt(n0inv.intValue());
        putLittleEndian(b, n, bytes);
        putLittleEndian(b, rr, bytes);
        b.putInt(key.getPublicExponent().intValue());
        String encoded = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " " + name + "\0";
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    private static void putLittleEndian(ByteBuffer b, BigInteger value, int length) {
        byte[] bigEndian = value.toByteArray();
        byte[] little = new byte[length];
        for (int i = 0; i < length && i < bigEndian.length; i++) {
            little[i] = bigEndian[bigEndian.length - 1 - i];
        }
        b.put(little);
    }

    static KeyPair loadOrCreateKeys(File dir) throws GeneralSecurityException, IOException {
        File privateFile = new File(dir, "adbkey");
        File publicFile = new File(dir, "adbkey.pub");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (privateFile.exists() && publicFile.exists()) {
            return new KeyPair(
                    factory.generatePublic(new X509EncodedKeySpec(readAll(publicFile))),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(readAll(privateFile))));
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        writeAll(privateFile, keys.getPrivate().getEncoded());
        writeAll(publicFile, keys.getPublic().getEncoded());
        return keys;
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] data = new byte[(int) f.length()];
        try (DataInputStream s = new DataInputStream(new FileInputStream(f))) {
            s.readFully(data);
        }
        return data;
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        try (FileOutputStream s = new FileOutputStream(f)) {
            s.write(data);
        }
    }
}
