package wallet.util;

import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
// Base58 đổi package giữa các bản bitcoinj
// 0.15 / 0.16: org.bitcoinj.core.Base58
// 0.17+: org.bitcoinj.base.Base58
// nếu báo đỏ thì đổi import cho đúng bản của bạn
import org.bitcoinj.core.Base58;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.SCrypt;

public class Bip38Helper {

    public static String encrypt(ECKey key, String passphrase, Network network) throws Exception {
        byte[] privKeyBytes = key.getPrivKeyBytes();
        boolean compressed = key.isCompressed();

        // BIP38 addresshash luôn dùng địa chỉ P2PKH legacy, không dùng bech32
        String address = key.toAddress(ScriptType.P2PKH, network).toString();
        byte[] addressHash = doubleSha256(address.getBytes(StandardCharsets.UTF_8));
        addressHash = Arrays.copyOf(addressHash, 4);

        // scrypt N=16384, r=8, p=8
        byte[] derived = SCrypt.generate(
                passphrase.getBytes(StandardCharsets.UTF_8),
                addressHash,
                16384, 8, 8, 64
        );
        byte[] derivedHalf1 = Arrays.copyOfRange(derived, 0, 32);
        byte[] derivedHalf2 = Arrays.copyOfRange(derived, 32, 64);

        byte[] xor = new byte[32];
        for (int i = 0; i < 32; i++) {
            xor[i] = (byte) (privKeyBytes[i] ^ derivedHalf1[i]);
        }

        Cipher aes;
        try {
            aes = Cipher.getInstance("AES/ECB/NoPadding", "SC"); // SpongyCastle
        } catch (Exception e) {
            aes = Cipher.getInstance("AES/ECB/NoPadding", "BC"); // BouncyCastle fallback
        }
        SecretKeySpec keySpec = new SecretKeySpec(derivedHalf2, "AES");
        aes.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedHalf1 = aes.doFinal(Arrays.copyOfRange(xor, 0, 16));
        byte[] encryptedHalf2 = aes.doFinal(Arrays.copyOfRange(xor, 16, 32));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01);
        baos.write(0x42);
        int flag = 0xC0 | (compressed? 0x20 : 0x00); // 0xE0 compressed, 0xC0 uncompressed
        baos.write(flag);
        baos.write(addressHash);
        baos.write(encryptedHalf1);
        baos.write(encryptedHalf2);

        byte[] payload = baos.toByteArray();
        byte[] checksum = doubleSha256(payload);
        checksum = Arrays.copyOf(checksum, 4);

        byte[] full = new byte[payload.length + 4];
        System.arraycopy(payload, 0, full, 0, payload.length);
        System.arraycopy(checksum, 0, full, payload.length, 4);

        return Base58.encode(full);
    }

    private static byte[] doubleSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(digest.digest(data));
    }
}
