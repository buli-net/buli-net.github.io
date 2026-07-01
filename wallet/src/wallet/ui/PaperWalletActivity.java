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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import wallet.Constants;
import wallet.R;
import wallet.util.Qr;

public class PaperWalletActivity extends AbstractWalletActivity {
    private static final int QR_SIZE = 512;

    private View cardView;
    private ImageView qrAddressView, qrKeyView;
    private TextView addressView, pubKeyView, privKeyView, addressTypeView, privKeyLabelView;
    private Button toggleKeyButton, privKeyFormatBtn, exportTxtBtn;
    private boolean keyVisible = true;
    private boolean privKeyHexMode = false;

    private String currentAddress = "";
    private String currentPubKey = "";
    private String currentPrivKeyWif = "";
    private String currentPrivKeyHex = "";

    private ScriptType addressType = ScriptType.P2WPKH;

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

        if (qrAddressView != null) qrAddressView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (qrKeyView != null) qrKeyView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        findViewById(R.id.paper_wallet_copy_address).setOnClickListener(v -> copyText("Address", currentAddress));
        findViewById(R.id.paper_wallet_copy_pubkey).setOnClickListener(v -> copyText("Public key", currentPubKey));
        findViewById(R.id.paper_wallet_copy_privkey).setOnClickListener(v -> copyText("Private key", privKeyHexMode ? currentPrivKeyHex : currentPrivKeyWif));

        privKeyView.setOnClickListener(v -> togglePrivKeyFormat());

        toggleKeyButton = findViewById(R.id.paper_wallet_toggle_key);
        toggleKeyButton.setOnClickListener(v -> toggleKeyVisibility());

        privKeyFormatBtn = findViewById(R.id.paper_wallet_privkey_format);
        if (privKeyFormatBtn != null) {
            privKeyFormatBtn.setOnClickListener(v -> togglePrivKeyFormat());
        }

        findViewById(R.id.paper_wallet_generate).setOnClickListener(v -> generateNew());

        View saveBtn = findViewById(R.id.paper_wallet_save);
        saveBtn.setOnClickListener(v -> savePaperWallet());
        saveBtn.setOnLongClickListener(v -> { exportWalletTxt(); return true; });

        findViewById(R.id.paper_wallet_share).setOnClickListener(v -> sharePaperWallet());

        View printBtn = findViewById(R.id.paper_wallet_print);
        if (printBtn != null) {
            printBtn.setVisibility(View.VISIBLE);
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
        final ECKey key = new ECKey();

        currentAddress = key.toAddress(addressType, network).toString();
        currentPubKey = key.getPublicKeyAsHex();
        currentPrivKeyWif = key.getPrivateKeyAsWiF(network);
        currentPrivKeyHex = key.getPrivateKeyAsHex();
        privKeyHexMode = false;

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
        qrKeyView.setImageBitmap(makeQr(currentPrivKeyWif));

        Toast.makeText(this, R.string.paper_wallet_generated, Toast.LENGTH_SHORT).show();
    }

    private void updatePrivKeyView() {
        if (keyVisible) {
            String keyText = privKeyHexMode ? currentPrivKeyHex : currentPrivKeyWif;
            privKeyView.setText(keyText);
            toggleKeyButton.setText(R.string.paper_wallet_hide_key);
            if (privKeyFormatBtn != null) privKeyFormatBtn.setText(privKeyHexMode ? "HEX" : "WIF");
            if (privKeyLabelView != null) {
                String base = getString(R.string.paper_wallet_key_label);
                privKeyLabelView.setText(base + (privKeyHexMode ? " (HEX)" : " (WIF)"));
            }
        } else {
            privKeyView.setText("••••••••••••••••••••••••");
            toggleKeyButton.setText(R.string.paper_wallet_show_key);
        }
    }

    private void toggleKeyVisibility() {
        keyVisible = !keyVisible;
        updatePrivKeyView();
    }

    private void togglePrivKeyFormat() {
        if (!keyVisible) return;
        privKeyHexMode = !privKeyHexMode;
        updatePrivKeyView();
        Toast.makeText(this, privKeyHexMode ? "Private key: HEX" : "Private key: WIF", Toast.LENGTH_SHORT).show();
    }

    private void copyText(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    // render cho in / share / save - nền trắng chữ đen
    private Bitmap buildPrintBitmap() {
        View printView = getLayoutInflater().inflate(R.layout.paper_wallet_print, null);
        
        ((TextView) printView.findViewById(R.id.print_address)).setText(currentAddress);
        ((TextView) printView.findViewById(R.id.print_pubkey)).setText(currentPubKey);
        ((TextView) printView.findViewById(R.id.print_privkey)).setText(privKeyHexMode ? currentPrivKeyHex : currentPrivKeyWif);
        ((TextView) printView.findViewById(R.id.print_address_type)).setText(
            addressType == ScriptType.P2PKH ? "Legacy P2PKH" : "SegWit bech32"
        );
        ((ImageView) printView.findViewById(R.id.print_qr_address)).setImageBitmap(makeQr(currentAddress));
        ((ImageView) printView.findViewById(R.id.print_qr_key)).setImageBitmap(makeQr(currentPrivKeyWif));

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
            String content = "Paper Wallet\n"
                + "Type: " + typeName + "\n"
                + "Address: " + currentAddress + "\n"
                + "Public key: " + currentPubKey + "\n"
                + "Private key WIF: " + currentPrivKeyWif + "\n"
                + "Private key HEX: " + currentPrivKeyHex + "\n";

            String filename = "paperwallet_" + System.currentTimeMillis() + ".txt";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PaperWallet");

            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
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
}
