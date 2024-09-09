class UTXO {
    String txid;
    int vout;
    long value;

    public UTXO(String txid, int vout, long value) {
        this.txid = txid;
        this.vout = vout;
        this.value = value;
    }
}
