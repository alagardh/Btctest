import java.util.Arrays;

public class Bech32 {

    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] GENERATOR = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

    public static class Bech32Data {
        public final String hrp;
        public final byte[] data;

        public Bech32Data(String hrp, byte[] data) {
            this.hrp = hrp;
            this.data = data;
        }
    }

    public static String encode(String hrp, int witnessVersion, byte[] witnessProgram) throws Exception {
        // Convert witness version and program to 5-bit words
        byte[] convertedProgram = convertBits(witnessProgram, 8, 5, true);  // Convert to 5-bit words
        
        // Ensure the size of the data array is appropriate (witness version + converted program)
        byte[] data = new byte[convertedProgram.length + 1];
        
        // Set witness version (0 for SegWit), convert to 5-bit representation by adding 0x00 (witness version)
        data[0] = (byte) witnessVersion;  
        
        // Copy the converted witness program into the data array
        System.arraycopy(convertedProgram, 0, data, 1, convertedProgram.length);
        
        // Create Bech32Data object and call the existing encode method
        Bech32Data bech32Data = new Bech32Data(hrp, data);
        return encode(bech32Data);
    }


    public static String encode(Bech32Data bech32Data) throws Exception {
        byte[] combined = new byte[bech32Data.data.length + 6];
        System.arraycopy(bech32Data.data, 0, combined, 0, bech32Data.data.length);
        byte[] checksum = createChecksum(bech32Data.hrp, bech32Data.data);
        System.arraycopy(checksum, 0, combined, bech32Data.data.length, checksum.length);
        StringBuilder sb = new StringBuilder(bech32Data.hrp + "1");
        for (byte b : combined) {
            sb.append(CHARSET.charAt(b));
        }
        return sb.toString();
    }

    public static byte[] convertBits(byte[] data, int fromBits, int toBits, boolean pad) throws Exception {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toBits) - 1;
        byte[] result = new byte[(data.length * fromBits + toBits - 1) / toBits];
        int resultIndex = 0;
        
        for (byte value : data) {
            if ((value & 0xff) >> fromBits != 0) {
                throw new Exception("Invalid data value");
            }
            acc = (acc << fromBits) | (value & 0xff);
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                result[resultIndex++] = (byte) ((acc >> bits) & maxv);
            }
        }

        if (pad) {
            if (bits > 0) {
                result[resultIndex++] = (byte) ((acc << (toBits - bits)) & maxv);
            }
        } else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) != 0) {
            throw new Exception("Invalid padding in convertBits()");
        }
        
        return Arrays.copyOf(result, resultIndex);
    }


    private static byte[] createChecksum(String hrp, byte[] data) {
        byte[] values = expandHrp(hrp);
        byte[] combined = new byte[values.length + data.length + 6];
        System.arraycopy(values, 0, combined, 0, values.length);
        System.arraycopy(data, 0, combined, values.length, data.length);
        int mod = polymod(combined);
        byte[] ret = new byte[6];
        for (int i = 0; i < 6; ++i) {
            ret[i] = (byte) ((mod >> 5 * (5 - i)) & 31);
        }
        return ret;
    }

    private static byte[] expandHrp(String hrp) {
        byte[] ret = new byte[hrp.length() * 2 + 1];
        for (int i = 0; i < hrp.length(); ++i) {
            ret[i] = (byte) (hrp.charAt(i) >> 5);
            ret[i + hrp.length() + 1] = (byte) (hrp.charAt(i) & 31);
        }
        return ret;
    }

    private static int polymod(byte[] values) {
        int chk = 1;
        for (byte value : values) {
            byte top = (byte) (chk >> 25);
            chk = ((chk & 0x1ffffff) << 5) ^ (value & 0xff);
            for (int i = 0; i < 5; ++i) {
                if (((top >> i) & 1) != 0) {
                    chk ^= GENERATOR[i];
                }
            }
        }
        return chk ^ 1;
    }
    
    public static Bech32Data decode(String bech) throws Exception {
        if (bech.length() < 8 || bech.length() > 90) {
            throw new Exception("Invalid Bech32 string length");
        }

        int pos = bech.lastIndexOf('1');
        if (pos == -1) {
            throw new Exception("Invalid Bech32 string: no separator '1' found");
        }

        String hrp = bech.substring(0, pos);
        byte[] data = new byte[bech.length() - pos - 1];
        for (int i = 0; i < data.length; i++) {
            int ch = bech.charAt(i + pos + 1);
            int index = CHARSET.indexOf(ch);
            if (index == -1) {
                throw new Exception("Invalid Bech32 character: " + ch);
            }
            data[i] = (byte) index;
        }
        return new Bech32Data(hrp, data);
    }

    
}
