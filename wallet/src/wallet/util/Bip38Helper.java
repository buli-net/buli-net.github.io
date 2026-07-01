package wallet.util;

import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.SCrypt;

public class Bip38Helper {

    private static final char[] BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    public static String encrypt(ECKey key, String passphrase, Network network) throws Exception {
        byte[] privKeyBytes = key.getPrivKeyBytes();
        boolean compressed = key.isCompressed();

        // BIP38 addresshash luôn dùng địa chỉ P2PKH legacy
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
            aes = Cipher.getInstance("AES/ECB/NoPadding", "SC");
        } catch (Exception e) {
            aes = Cipher.getInstance("AES/ECB/NoPadding", "BC");
        }
        SecretKeySpec keySpec = new SecretKeySpec(derivedHalf2, "AES");
        aes.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedHalf1 = aes.doFinal(Arrays.copyOfRange(xor, 0, 16));
        byte[] encryptedHalf2 = aes.doFinal(Arrays.copyOfRange(xor, 16, 32));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01);
        baos.write(0x42);
        int flag = 0xC0 | (compressed? 0x20 : 0x00);
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

        return base58Encode(full);
    }

    private static byte[] doubleSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(digest.digest(data));
    }

    private static String base58Encode(byte[] input) {
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) zeros++;
        byte[] temp = Arrays.copyOf(input, input.length);
        char[] encoded = new char[temp.length * 2];
        int outputStart = encoded.length;
        int inputStart = zeros;
        while (inputStart < temp.length) {
            int mod = divmod58(temp, inputStart);
            if (temp[inputStart] == 0) inputStart++;
            encoded[--outputStart] = BASE58_ALPHABET[mod];
        }
        while (outputStart < encoded.length && encoded[outputStart] == BASE58_ALPHABET[0]) outputStart++;
        while (--zeros >= 0) encoded[--outputStart] = BASE58_ALPHABET[0];
        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    private static int divmod58(byte[] number, int startAt) {
        int remainder = 0;
        for (int i = startAt; i < number.length; i++) {
            int digit256 = number[i] & 0xFF;
            int temp = remainder * 256 + digit256;
            number[i] = (byte) (temp / 58);
            remainder = temp % 58;
        }
        return remainder;
    }
}
