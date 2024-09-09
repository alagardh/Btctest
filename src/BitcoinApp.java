import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.*;
import java.security.spec.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class BitcoinApp {
    private static JTable utxoTable;
    private static DefaultTableModel utxoTableModel;
    private static final String SIGHASH_ALL = "01";

    
    public static void main(String[] args) {
        // Register BouncyCastle as a security provider
        Security.addProvider(new BouncyCastleProvider());

        // Create the JFrame
        JFrame frame = new JFrame("Bitcoin Wallet Generator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set the frame size to 75% of the screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.75);
        int height = (int) (screenSize.height * 0.75);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null); // Center the window on screen

        // Create a JPanel and layout
        JPanel panel = new JPanel();
        frame.add(panel);
        placeComponents(panel);

        // Make the frame visible
        frame.setVisible(true);
    }

    private static void placeComponents(JPanel panel) {
        // Using GridBagLayout for better control over component placement
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);  // Padding between components

        // Label for Bech32 Address
        JLabel addressLabel = new JLabel("Bech32 Address:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(addressLabel, gbc);

        // TextField for Bech32 Address
        JTextField walletAddressField = new JTextField(40);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        walletAddressField.setEditable(false);
        panel.add(walletAddressField, gbc);

        // Label for Private Key
        JLabel privateKeyLabel = new JLabel("Private Key:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(privateKeyLabel, gbc);

        // TextField for Private Key (Password field to mask)
        JPasswordField privateKeyField = new JPasswordField(40);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        privateKeyField.setEditable(false);
        panel.add(privateKeyField, gbc);

        // Button to List UTXOs
        JButton listUTXOsButton = new JButton("List UTXOs");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(listUTXOsButton, gbc);

        // Button to Generate Wallet
        JButton generateButton = new JButton("Generate Wallet");
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(generateButton, gbc);

        // Button to Open Folder
        JButton openButton = new JButton("Open Folder");
        gbc.gridx = 2;
        gbc.gridy = 2;
        panel.add(openButton, gbc);

        // Table to display UTXOs
        String[] columns = {"UTXO", "Value (sats)", "Select"};
     // Update DefaultTableModel to specify the data type for each column
        utxoTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                // Return Boolean.class for the "Select" column, otherwise String.class
                return (column == 2) ? Boolean.class : String.class;
            }
        };
        utxoTable = new JTable(utxoTableModel);
        JScrollPane scrollPane = new JScrollPane(utxoTable);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel.add(scrollPane, gbc);

        // Set up action listeners for buttons
        listUTXOsButton.addActionListener(e -> {
            try {
                String walletAddress = readWalletAddressFromFile();
                if (walletAddress != null) {
                    walletAddressField.setText(walletAddress);
                    fetchAndDisplayUTXOs(walletAddress);
                } else {
                    JOptionPane.showMessageDialog(null, "No wallet address found in file.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

     // Button to create a raw transaction
        JButton createRawTxButton = new JButton("Create Raw Transaction");
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(createRawTxButton, gbc);

        // TextArea to display raw transaction
        JTextArea rawTransactionField = new JTextArea(5, 40);
        rawTransactionField.setLineWrap(true);
        rawTransactionField.setWrapStyleWord(true);
        JScrollPane txScrollPane = new JScrollPane(rawTransactionField);
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(txScrollPane, gbc);

        createRawTxButton.addActionListener(e -> {
            try {
                // Gather the selected UTXOs from the table
                List<UTXO> selectedUTXOs = getSelectedUTXOsFromTable();

                // Prompt for the destination address and amount to send
                String destinationAddress = JOptionPane.showInputDialog("Enter destination address:");
                String amountStr = JOptionPane.showInputDialog("Enter amount to send (in sats):");
                long amount = Long.parseLong(amountStr);

                // Generate raw transaction
                String rawTransaction = createRawTransaction(destinationAddress, amount, selectedUTXOs, extractPrivateKey());
                
                // Display the raw transaction in the text area
                rawTransactionField.setText(rawTransaction);
                
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Error creating raw transaction: " + ex.getMessage());
            }
        });

        generateButton.addActionListener(e -> {
            try {
                // Generate the wallet and display in the UI
                String[] walletData = generateWallet();
                String walletAddress = walletData[0];
                String privateKeyHex = walletData[1];

                // Set values in the text fields
                walletAddressField.setText(walletAddress);
                privateKeyField.setText(privateKeyHex);

                // Save the wallet details to a file in C:\btcaddress
                saveWalletToFile(walletAddress, privateKeyHex);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        openButton.addActionListener(e -> {
            try {
                // Open the folder C:\btcaddress
                Desktop.getDesktop().open(new File("C:\\btcaddress"));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    public static String extractPrivateKey() {
    	try {
            File file = new File("C:\\btcaddress\\wallet_info.txt");
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("Private Key:")) {
                        return line.split(":")[1].trim();
                    }
                }
                br.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    

    // Method to read wallet address from file
    private static String readWalletAddressFromFile() {
        try {
            File file = new File("C:\\btcaddress\\wallet_info.txt");
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("Wallet Address:")) {
                        return line.split(":")[1].trim();
                    }
                }
                br.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
 // Method to fetch UTXOs and display in the table
    private static void fetchAndDisplayUTXOs(String address) {
    	// Adding UTXOs with an unchecked checkbox (Boolean.FALSE) in the "Select" column
        try {
            // Use Mempool.space Testnet API to get UTXOs
            URL url = new URL("https://mempool.space/testnet/api/address/" + address + "/utxo");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                StringBuilder inline = new StringBuilder();
                Scanner sc = new Scanner(url.openStream());
                while (sc.hasNext()) {
                    inline.append(sc.nextLine());
                }
                sc.close();

                // Parse JSON response
                JSONArray utxoArray = new JSONArray(inline.toString());
                utxoTableModel.setRowCount(0); // Clear existing table content

                if (utxoArray.length() == 0) {
                    // No UTXOs found, display an alert
                    JOptionPane.showMessageDialog(null, "No UTXOs found for this address.");
                    return;
                }

                boolean hasValidUTXO = false;

                for (int i = 0; i < utxoArray.length(); i++) {
                    JSONObject utxo = utxoArray.getJSONObject(i);
                    String txid = utxo.getString("txid");
                    int vout = utxo.getInt("vout");
                    long value = utxo.getLong("value");

                    // Add UTXO to table only if value > 0
                    if (value > 0) {
                    	utxoTableModel.addRow(new Object[]{txid + ":" + vout, value, Boolean.FALSE});
                        hasValidUTXO = true;
                    }
                }

                // If no valid UTXOs (value > 0), show an alert
                if (!hasValidUTXO) {
                    JOptionPane.showMessageDialog(null, "All UTXOs have zero balance.");
                }

            } else {
                JOptionPane.showMessageDialog(null, "Failed to fetch UTXOs.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private static String[] generateWallet() throws Exception {
        // 1. Generate a random private key using secp256k1
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
        keyGen.initialize(ecSpec, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // Display the private key in hexadecimal format
        String privateKeyHex = Hex.toHexString(privateKey.getEncoded());

        // 2. Hash the public key using SHA-256 and then RIPEMD-160
        byte[] publicKeyBytes = publicKey.getEncoded();
        byte[] sha256Hash = sha256(publicKeyBytes);
        byte[] ripemd160Hash = ripemd160(sha256Hash);

        // 3. Prepare the witness program (hash of public key) and witness version (0)
        byte[] witnessProgram = ripemd160Hash;

        // 4. Encode the witness program into a Bech32 address
        String bech32Address = Bech32.encode("tb", 0x00, ripemd160Hash);  // "tb" for Testnet and witness version 0

        // Return both the address and private key
        return new String[]{bech32Address, privateKeyHex};
    }

    private static void saveWalletToFile(String walletAddress, String privateKey) {
        // Define the folder and file path
        String folderPath = "C:\\btcaddress";
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdir(); // Create folder if it doesn't exist
        }

        // Create a file with the wallet information
        File walletFile = new File(folderPath + "\\wallet_info.txt");

        try (FileWriter writer = new FileWriter(walletFile)) {
            writer.write("Wallet Address: " + walletAddress + "\n");
            writer.write("Private Key: " + privateKey + "\n");
            System.out.println("Wallet saved to: " + walletFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] sha256(byte[] input) {
        SHA256Digest digest = new SHA256Digest();
        byte[] sha256Hash = new byte[digest.getDigestSize()];
        digest.update(input, 0, input.length);
        digest.doFinal(sha256Hash, 0);
        return sha256Hash;
    }

    private static byte[] ripemd160(byte[] input) {
        RIPEMD160Digest digest = new RIPEMD160Digest();
        byte[] ripemd160Hash = new byte[digest.getDigestSize()];
        digest.update(input, 0, input.length);
        digest.doFinal(ripemd160Hash, 0);
        return ripemd160Hash;
    }
    
    public static String createRawTransaction(String destinationAddress, long amount, List<UTXO> selectedUTXOs, String privateKey) throws Exception {
        StringBuilder rawTxBuilder = new StringBuilder();

        // Version (4 bytes, little-endian)
        rawTxBuilder.append("01000000"); // Version 1

        // Input count (varint)
        rawTxBuilder.append(String.format("%02x", selectedUTXOs.size()));

        // Inputs (UTXOs)
        for (UTXO utxo : selectedUTXOs) {
            rawTxBuilder.append(reverseHex(utxo.txid)); // TXID of UTXO (32 bytes, little-endian)
            rawTxBuilder.append(String.format("%08x", utxo.vout)); // VOUT index (4 bytes, little-endian)
            rawTxBuilder.append("00"); // ScriptSig length (0 for now)
            rawTxBuilder.append("ffffffff"); // Sequence (4 bytes)
        }

        // Output count (1 output)
        rawTxBuilder.append("01");

        // Output (sending amount and destination)
        rawTxBuilder.append(String.format("%016x", amount)); // Amount in satoshis (8 bytes, little-endian)

        // ScriptPubKey for P2PKH
        rawTxBuilder.append("1976a914"); // OP_DUP OP_HASH160
        rawTxBuilder.append(getHash160FromBech32Address(destinationAddress)); // PubKeyHash (20 bytes)
        rawTxBuilder.append("88ac"); // OP_EQUALVERIFY OP_CHECKSIG

        // Locktime (4 bytes, set to 0)
        rawTxBuilder.append("00000000");

        // Sighash type (SIGHASH_ALL)
        rawTxBuilder.append(SIGHASH_ALL);

        return rawTxBuilder.toString();
    }

    public static String getHash160FromBech32Address(String address) throws Exception {
        Bech32.Bech32Data decoded = Bech32.decode(address);
        if (decoded == null || decoded.data.length < 1) {
            throw new Exception("Invalid Bech32 address");
        }

        // Convert the Bech32 5-bit data into 8-bit format (witness program)
        byte[] program = Bech32.convertBits(Arrays.copyOfRange(decoded.data, 1, decoded.data.length), 5, 8, false);
        
        // Convert the result to hex
        return bytesToHex(program);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }


    // Helper function to reverse the byte order of the hex string
    private static String reverseHex(String hex) {
        StringBuilder result = new StringBuilder();
        for (int i = hex.length(); i > 0; i -= 2) {
            result.append(hex.substring(i - 2, i));
        }
        return result.toString();
    }

    private static List<UTXO> getSelectedUTXOsFromTable() {
        List<UTXO> selectedUTXOs = new ArrayList<>();
        for (int i = 0; i < utxoTableModel.getRowCount(); i++) {
            Boolean isSelected = (Boolean) utxoTableModel.getValueAt(i, 2); // Check the "Select" column
            if (isSelected != null && isSelected) {
                String[] utxoData = ((String) utxoTableModel.getValueAt(i, 0)).split(":");
                String txid = utxoData[0];
                int vout = Integer.parseInt(utxoData[1]);
                long value = (Long) utxoTableModel.getValueAt(i, 1);
                selectedUTXOs.add(new UTXO(txid, vout, value));
            }
        }
        return selectedUTXOs;
    }

}
