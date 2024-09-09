import java.math.BigInteger;
import java.util.Arrays;

public class Base58 {
    private static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length);
    private static final int[] INDEXES = new int[128];

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            INDEXES[ALPHABET[i]] = i;
        }
    }

    public static byte[] decode(String input) throws Exception {
        if (input.length() == 0) {
            return new byte[0];
        }

        BigInteger intData = BigInteger.ZERO;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int digit = INDEXES[c];
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid Base58 character: " + c);
            }
            intData = intData.multiply(BASE).add(BigInteger.valueOf(digit));
        }

        // Convert BigInteger to byte array
        byte[] intDataBytes = intData.toByteArray();

        // Strip sign byte if present
        if (intDataBytes[0] == 0) {
            intDataBytes = Arrays.copyOfRange(intDataBytes, 1, intDataBytes.length);
        }

        // Count leading zeros
        int leadingZeros = 0;
        for (int i = 0; i < input.length() && input.charAt(i) == ALPHABET[0]; i++) {
            leadingZeros++;
        }

        // Add leading zeros
        byte[] decoded = new byte[leadingZeros + intDataBytes.length];
        System.arraycopy(intDataBytes, 0, decoded, leadingZeros, intDataBytes.length);

        return decoded;
    }
}
