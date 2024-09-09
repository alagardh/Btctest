import java.math.BigInteger;
import java.util.Arrays;

public class Base58Check {
    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);

    public static String encode(byte[] input) {
        BigInteger intData = new BigInteger(1, input);
        StringBuilder result = new StringBuilder();

        while (intData.compareTo(BASE) >= 0) {
            BigInteger[] divideRemainder = intData.divideAndRemainder(BASE);
            intData = divideRemainder[0];
            int remainder = divideRemainder[1].intValue();
            result.insert(0, ALPHABET.charAt(remainder));
        }
        result.insert(0, ALPHABET.charAt(intData.intValue()));

        // Leading zeroes (0x00 bytes)
        for (byte b : input) {
            if (b == 0x00) {
                result.insert(0, ALPHABET.charAt(0));
            } else {
                break;
            }
        }

        return result.toString();
    }
}
