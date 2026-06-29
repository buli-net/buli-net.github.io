package wallet.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.Wallet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;

import wallet.R;
import wallet.WalletApplication;

import org.bitcoinj.core.TransactionOutPoint;

public class TransactionDetailsActivity extends Activity {
    private TextView tvDirection, tvAmount, tvStatus, tvFee, tvTime, tvFrom, tvTo, tvTxid, tvHeight, tvMeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        tvDirection = findViewById(R.id.tv_direction);
        tvAmount = findViewById(R.id.tv_amount);
        tvStatus = findViewById(R.id.tv_status);
        tvFee = findViewById(R.id.tv_fee);
        tvTime = findViewById(R.id.tv_time);
        tvFrom = findViewById(R.id.tv_from);
        tvTo = findViewById(R.id.tv_to);
        tvTxid = findViewById(R.id.tv_txid);
        tvHeight = findViewById(R.id.tv_height);
        tvMeta = findViewById(R.id.tv_meta);

        String txidStr = getIntent().getStringExtra("txid");
        if (txidStr == null) { finish(); return; }

        WalletApplication app = (WalletApplication) getApplication();
        Wallet wallet = app.getWallet();
        NetworkParameters params = wallet.getNetworkParameters();

        Transaction tx = wallet.getTransaction(Sha256Hash.wrap(txidStr));
        if (tx == null) { finish(); return; }

        Coin value = tx.getValue(wallet);
        boolean isSend = value.isNegative();
        Coin absValue = isSend ? value.negate() : value;

        tvDirection.setText(isSend ? "Sent" : "Receive");
        tvAmount.setText((isSend ? "-" : "+") + absValue.toPlainString() + " BTC");
        tvAmount.setTextColor(getResources().getColor(isSend ? R.color.tx_amount_sent : R.color.tx_amount_recv));

        TransactionConfidence confidence = tx.getConfidence();
        int depth = confidence.getDepthInBlocks();
        boolean confirmed = confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING;
        tvStatus.setText(confirmed ? "Confirmed" : "Pending");
        tvStatus.setTextColor(getResources().getColor(confirmed ? R.color.tx_status_ok : R.color.tx_status_pending));

        Coin fee = tx.getFee();
        tvFee.setText(fee != null ? fee.toPlainString() + " BTC" : "—");

        Date updateTime = tx.getUpdateTime();
        if (updateTime != null) {
            tvTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(updateTime));
        }

        int height = confidence.getAppearedAtChainHeight();
        String confStr = (depth > 0 ? depth + " confirmations" : "unconfirmed") +
                (height > 0 ? " · height " + height : "");
        tvHeight.setText(confStr);

        int size = tx.getMessageSize();
        int weight = tx.getWeight();
        boolean rbf = tx.isOptInFullRBF();
        String feeRate = "";
        if (fee != null && weight > 0) {
            long satPerVbyte = fee.getValue() * 4 / weight;
            feeRate = " · " + satPerVbyte + " sat/vB";
        }
        tvMeta.setText(size + " bytes · " + weight + " wu" + feeRate + (rbf ? " · RBF" : ""));

        LinkedHashSet<String> fromAddrs = new LinkedHashSet<>();
        for (TransactionInput in : tx.getInputs()) {
            try {
                TransactionOutPoint outpoint = in.getOutpoint();
                if (outpoint != null && outpoint.getConnectedOutput() != null) {
                    Script script = outpoint.getConnectedOutput().getScriptPubKey();
                    String a = getAddressFromScript(script, params);
                    if (a != null) fromAddrs.add(a);
                }
            } catch (Exception ignored) {}
            if (fromAddrs.isEmpty()) {
                try { fromAddrs.add(in.getOutpoint().toString()); } catch (Exception ignored) {}
            }
        }

        LinkedHashSet<String> toAddrs = new LinkedHashSet<>();
        for (TransactionOutput out : tx.getOutputs()) {
            try {
                String a = getAddressFromScript(out.getScriptPubKey(), params);
                if (a != null) toAddrs.add(a + " · " + out.getValue().toPlainString() + " BTC");
                else toAddrs.add(out.getScriptPubKey().toString() + " · " + out.getValue().toPlainString() + " BTC");
            } catch (Exception ignored) {}
        }

        String fromText = fromAddrs.isEmpty() ? "coinbase" : String.join("\n", fromAddrs);
        String toText = String.join("\n", toAddrs);

        tvFrom.setText(fromText);
        tvTo.setText(toText);
        copyOnClick(tvFrom, fromText);
        copyOnClick(tvTo, toText);

        String hash = tx.getTxId().toString();
        tvTxid.setText(hash);
        copyOnClick(tvTxid, hash);
    }

    private String getAddressFromScript(Script script, NetworkParameters params) {
        try {
            if (ScriptPattern.isP2PKH(script)) return ScriptPattern.extractHashFromP2PKH(script).toString();
            if (ScriptPattern.isP2WPKH(script)) return ScriptPattern.extractHashFromP2WH(script).toString();
            if (ScriptPattern.isP2SH(script)) return ScriptPattern.extractHashFromP2SH(script).toString();
            return script.getToAddress(params).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void copyOnClick(TextView tv, String text) {
        tv.setOnClickListener(v -> copy(text));
    }
    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("tx", text));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }
}
