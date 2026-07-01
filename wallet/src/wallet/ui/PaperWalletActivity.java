package wallet.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.print.PrintHelper;

import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.base.Network;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.core.NetworkParameters;

// BIP38 - có sẵn trong bitcoinj 0.16+
// Nếu bản bitcoinj của bạn cũ hơn, thêm:
// implementation 'org.bitcoinj:bitcoinj-core:0.16.2'
import org.bitcoinj.crypto.BIP38PrivateKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import wallet.Constants;
import wallet.R;
import wallet.util.Qr;

public class PaperWalletActivity extends AbstractWalletActivity {
    private static final int QR_SIZE = 512;

    private View cardView;
    private ImageView qrAddressView, qrKeyView;
    private TextView addressView, pubKeyView, privKeyView, addressTypeView, privKeyLabelView;
    private Button toggleKeyButton, privKeyFormatBtn, exportTxtBtn, generateBtn;
    private CheckBox encryptToggle;
    private EditText passView, passConfirmView;
    private TextView bip38HintView;

    private boolean keyVisible = true;
    private boolean privKeyHexMode = false;
    private boolean bip38Mode = false;

    private String currentAddress = "";
    private String currentPubKey = "";
    private String currentPrivKeyWif = "";
    private String currentPrivKeyHex = "";
    private String currentPrivKeyBip38 = "";

    private ScriptType addressType = ScriptType.P2WPKH;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String getFileProviderAuthority() {
        return getPackageName() + ".file_attachment";
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paper_wallet);

        if (getActionBar() != null)
            getActionBar().setDisplayHomeAsUpEnabled(true);

        cardView = findViewById(R.id.paper_wallet_card);
        qrAddressView = findViewById(R.id.paper_wallet_qr_address);
        qrKeyView = findViewById(R.id.paper_wallet_qr_key);
        addressView = findViewById(R.id.paper_wallet_address);
        pubKeyView = findViewById(R.id.paper_wallet_pubkey);
        privKeyView = findViewById(R.id.paper_wallet_key);
        addressTypeView = findViewById(R.id.paper_wallet_address_type);
        privKeyLabelView = findViewById(R.id.paper_wallet_key_label);

        encryptToggle = findViewById(R.id.paper_wallet_encrypt_toggle);
        passView = findViewById(R.id.paper_wallet_passphrase);
        passConfirmView = findViewById(R.id.paper_wallet_passphrase_confirm);
        bip38HintView = findViewById(R.id.paper_wallet_bip38_hint);
        generateBtn = findViewById(R.id.paper_wallet_generate);

        if (qrAddressView != null) qrAddressView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (qrKeyView != null) qrKeyView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        findViewById(R.id.paper_wallet_copy_address).setOnClickListener(v -> copyText("Address", currentAddress));
        findViewById(R.id.paper_wallet_copy_pubkey).setOnClickListener(v -> copyText("Public key", currentPubKey));
        findViewById(R.id.paper_wallet_copy_privkey).setOnClickListener(v -> {
            String key = bip38Mode ? currentPrivKeyBip38 : (privKeyHexMode ? currentPrivKeyHex : currentPrivKeyWif);
            copyText("Private key", key);
        });

        privKeyView.setOnClickListener(v -> togglePrivKeyFormat());

        toggleKeyButton = findViewById(R.id.paper_wallet_toggle_key);
        toggleKeyButton.setOnClickListener(v -> toggleKeyVisibility());

        privKeyFormatBtn = findViewById(R.id.paper_wallet_privkey_format);
        if (privKeyFormatBtn != null) {
            privKeyFormatBtn.setOnClickListener(v -> togglePrivKeyFormat());
        }

        encryptToggle.setOnCheckedChangeListener((b, checked) -> {
            int vis = checked ? View.VISIBLE : View.GONE;
            passView.setVisibility(vis);
            passView.setEnabled(checked);
            passConfirmView.setVisibility(vis);
            passConfirmView.setEnabled(checked);
            bip38HintView.setVisibility(vis);
            updatePrivKeyView();
        });

        generateBtn.setOnClickListener(v -> generateNew());

        View saveBtn = findViewById(R.id.paper_wallet_save);
        saveBtn.setOnClickListener(v -> savePaperWallet());
        saveBtn.setOnLongClickListener(v -> { exportWalletTxt(); return true; });

        findViewById(R.id.paper_wallet_share).setOnClickListener(v -> sharePaperWallet());

        View printBtn = findViewById(R.id.paper_wallet_print);
        if (printBtn != null) {
            printBtn.setOnClickListener(v -> printPaperWallet());
        }

        exportTxtBtn = findViewById(R.id.paper_wallet_export_txt);
        if (exportTxtBtn != null) {
            exportTxtBtn.setOnClickListener(v -> exportWalletTxt());
        }

        if (addressTypeView != null) {
            addressTypeView.setOnClickListener(v -> {
                addressType = (addressType == ScriptType.P2PKH) ? ScriptType.P2WPKH : ScriptType.P2PKH;
                generateNew();
            });
        }

        generateNew();
    }

    private Network getNetwork() {
        NetworkParameters params = Constants.NETWORK_PARAMETERS;
        String id = params.getId().toLowerCase();
        if (id.contains("regtest")) return BitcoinNetwork.REGTEST;
        if (id.contains("test")) return BitcoinNetwork.TESTNET;
        return BitcoinNetwork.MAINNET;
    }

    private Bitmap makeQr(String text) {
        Bitmap qr = Qr.bitmap(text);
        return Bitmap.createScaledBitmap(qr, QR_SIZE, QR_SIZE, false);
    }

    private void generateNew() {
        final Network network = getNetwork();
        final boolean doBip38 = encryptToggle != null && encryptToggle.isChecked();
        String p1 = passView != null ? passView.getText().toString() : "";
        String p2 = passConfirmView != null ? passConfirmView.getText().toString() : "";

        if (doBip38) {
            if (p1.isEmpty()) {
                Toast.makeText(this, "Nhập passphrase cho BIP38", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!p1.equals(p2)) {
                Toast.makeText(this, "Passphrase không khớp", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        generateBtn.setEnabled(false);
        final ECKey key = new ECKey();

        currentAddress = key.toAddress(addressType, network).toString();
        currentPubKey = key.getPublicKeyAsHex();
        currentPrivKeyWif = key.getPrivateKeyAsWiF(network);
        currentPrivKeyHex = key.getPrivateKeyAsHex();
        privKeyHexMode = false;
        bip38Mode = false;

        addressView.setText(currentAddress);
        pubKeyView.setText(currentPubKey);
        updatePrivKeyView();

        if (addressTypeView != null) {
            String label = (addressType == ScriptType.P2PKH)
                ? "Legacy P2PKH (1...) - tap to switch"
                : "SegWit bech32 (bc1q...) - tap to switch";
            addressTypeView.setText(label);
        }

        qrAddressView.setImageBitmap(makeQr(currentAddress));

        if (!doBip38) {
            qrKeyView.setImageBitmap(makeQr(currentPrivKeyWif));
            generateBtn.setEnabled(true);
            Toast.makeText(this, R.string.paper_wallet_generated, Toast.LENGTH_SHORT).show();
            return;
        }

        // BIP38 encrypt off UI thread - scrypt nặng
        final String passphrase = p1;
        final ECKey keyFinal = key;
        Toast.makeText(this, "Encrypting BIP38...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                // API bitcoinj: BIP38PrivateKey.encrypt(...)
                // Nếu bản bitcoinj của bạn khác API, sửa 1 dòng này thôi
                BIP38PrivateKey bip38 = BIP38PrivateKey.encrypt(
                    network == BitcoinNetwork.MAINNET,
                    keyFinal.getPrivKey(),
                    keyFinal.isCompressed(),
                    passphrase
                );
                currentPrivKeyBip38 = bip38.toString();
                bip38Mode = true;
                runOnUiThread(() -> {
                    updatePrivKeyView();
                    generateBtn.setEnabled(true);
                    Toast.makeText(this, "Paper wallet BIP38 ready", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    generateBtn.setEnabled(true);
                    Toast.makeText(this, "BIP38 encrypt failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updatePrivKeyView() {
        String base = getString(R.string.paper_wallet_key_label);
        base = base.replaceAll("\\s*\\((WIF|HEX|BIP38)\\)\\s*$", "").trim();

        String displayKey;
        String suffix;
        if (bip38Mode && !currentPrivKeyBip38.isEmpty()) {
            displayKey = currentPrivKeyBip38;
            suffix = " (BIP38)";
        } else {
            displayKey = privKeyHexMode ? currentPrivKeyHex : currentPrivKeyWif;
            suffix = privKeyHexMode ? " (HEX)" : " (WIF)";
        }

        if (keyVisible) {
            privKeyView.setText(displayKey);
            toggleKeyButton.setText(R.string.paper_wallet_hide_key);
            if (privKeyFormatBtn != null) {
                privKeyFormatBtn.setEnabled(!bip38Mode);
                privKeyFormatBtn.setText(bip38Mode ? "BIP38" : (privKeyHexMode ? "HEX" : "WIF"));
                privKeyFormatBtn.setAlpha(bip38Mode ? 0.5f : 1.0f);
            }
            if (privKeyLabelView != null) privKeyLabelView.setText(base + suffix);
            if (qrKeyView != null && !displayKey.isEmpty()) qrKeyView.setImageBitmap(makeQr(displayKey));
        } else {
            privKeyView.setText("••••••••••••••••••••••••");
            toggleKeyButton.setText(R.string.paper_wallet_show_key);
            if (privKeyLabelView != null) privKeyLabelView.setText(base);
        }
    }

    private void toggleKeyVisibility() {
        keyVisible = !keyVisible;
        updatePrivKeyView();
    }

    private void togglePrivKeyFormat() {
        if (!keyVisible || bip38Mode) return;
        privKeyHexMode = !privKeyHexMode;
        updatePrivKeyView();
        Toast.makeText(this, privKeyHexMode ? "Private key: HEX" : "Private key: WIF", Toast.LENGTH_SHORT).show();
    }

    private void copyText(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    // Render cho in / share / save - nền trắng chữ đen
    private Bitmap buildPrintBitmap() {
        View printView = getLayoutInflater().inflate(R.layout.paper_wallet_print, null);
        
        String privKeyForPrint = bip38Mode && !currentPrivKeyBip38.isEmpty()
            ? currentPrivKeyBip38
            : (privKeyHexMode ? currentPrivKeyHex : currentPrivKeyWif);

        ((TextView) printView.findViewById(R.id.print_address)).setText(currentAddress);
        ((TextView) printView.findViewById(R.id.print_pubkey)).setText(currentPubKey);
        ((TextView) printView.findViewById(R.id.print_privkey)).setText(privKeyForPrint);
        ((TextView) printView.findViewById(R.id.print_address_type)).setText(
            addressType == ScriptType.P2PKH ? "Legacy P2PKH" : "SegWit bech32"
        );
        ((ImageView) printView.findViewById(R.id.print_qr_address)).setImageBitmap(makeQr(currentAddress));
        ((ImageView) printView.findViewById(R.id.print_qr_key)).setImageBitmap(makeQr(privKeyForPrint));

        TextView printPrivLabel = printView.findViewWithTag("print_privkey_label");
        if (printPrivLabel != null) {
            String s = "Private key" + (bip38Mode ? " (BIP38)" : (privKeyHexMode ? " (HEX)" : " (WIF)"));
            printPrivLabel.setText(s);
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        printView.measure(widthSpec, heightSpec);
        printView.layout(0, 0, printView.getMeasuredWidth(), printView.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(printView.getMeasuredWidth(), printView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFFFFFFF);
        printView.draw(canvas);
        return bitmap;
    }

    private File getShareFile() throws Exception {
        File dir = new File(getCacheDir(), "paperwallet");
        dir.mkdirs();
        File file = new File(dir, "paperwallet_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            buildPrintBitmap().compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    private void savePaperWallet() {
        try {
            Bitmap bitmap = buildPrintBitmap();
            String filename = "paperwallet_" + System.currentTimeMillis() + ".png";
            
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PaperWallet");
            
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Toast.makeText(this, "Saved to Pictures/PaperWallet/" + filename, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportWalletTxt() {
        try {
            String typeName = (addressType == ScriptType.P2PKH) ? "Legacy P2PKH" : "SegWit bech32";
            StringBuilder sb = new StringBuilder();
            sb.append("Paper Wallet\n");
            sb.append("Type: ").append(typeName).append("\n");
            sb.append("Address: ").append(currentAddress).append("\n");
            sb.append("Public key: ").append(currentPubKey).append("\n");
            sb.append("Private key WIF: ").append(currentPrivKeyWif).append("\n");
            sb.append("Private key HEX: ").append(currentPrivKeyHex).append("\n");
            if (bip38Mode && !currentPrivKeyBip38.isEmpty()) {
                sb.append("Private key BIP38: ").append(currentPrivKeyBip38).append("\n");
                sb.append("WARNING: BIP38 encrypted - passphrase required to spend\n");
            }

            String filename = "paperwallet_" + System.currentTimeMillis() + ".txt";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PaperWallet");

            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Exported to Documents/PaperWallet/" + filename, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePaperWallet() {
        try {
            File file = getShareFile();
            Uri uri = FileProvider.getUriForFile(this, getFileProviderAuthority(), file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.paper_wallet_share)));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void printPaperWallet() {
        try {
            PrintHelper helper = new PrintHelper(this);
            helper.setScaleMode(PrintHelper.SCALE_MODE_FIT);
            helper.printBitmap("Paper Wallet - " + currentAddress, buildPrintBitmap());
        } catch (Exception e) {
            Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
