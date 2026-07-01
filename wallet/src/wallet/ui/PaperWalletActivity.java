package wallet.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;

import java.io.File;
import java.io.FileOutputStream;

import wallet.Constants;
import wallet.R;
import wallet.util.Qr;

public class PaperWalletActivity extends AbstractWalletActivity {
    private View cardView;
    private ImageView qrAddressView, qrKeyView;
    private TextView addressView, pubKeyView, privKeyView;
    private Button toggleKeyButton;
    private boolean keyVisible = true;

    private String currentAddress = "";
    private String currentPubKey = "";
    private String currentPrivKey = "";

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

        findViewById(R.id.paper_wallet_copy_address).setOnClickListener(v -> copyText("Address", currentAddress));
        findViewById(R.id.paper_wallet_copy_pubkey).setOnClickListener(v -> copyText("Public key", currentPubKey));
        findViewById(R.id.paper_wallet_copy_privkey).setOnClickListener(v -> copyText("Private key", currentPrivKey));

        toggleKeyButton = findViewById(R.id.paper_wallet_toggle_key);
        toggleKeyButton.setOnClickListener(v -> toggleKeyVisibility());

        findViewById(R.id.paper_wallet_generate).setOnClickListener(v -> generateNew());
        findViewById(R.id.paper_wallet_save).setOnClickListener(v -> savePaperWallet());
        findViewById(R.id.paper_wallet_share).setOnClickListener(v -> sharePaperWallet());
        
        View printBtn = findViewById(R.id.paper_wallet_print);
        if (printBtn != null) printBtn.setVisibility(View.GONE);

        generateNew();
    }

    private void generateNew() {
        final NetworkParameters params = Constants.NETWORK_PARAMETERS;
        final ECKey key = new ECKey();

        currentAddress = key.toAddress(params).toString();
        currentPubKey = key.getPublicKeyAsHex();
        currentPrivKey = key.getPrivateKeyEncoded(params).toString();

        addressView.setText(currentAddress);
        pubKeyView.setText(currentPubKey);
        updatePrivKeyView();

        qrAddressView.setImageBitmap(Qr.bitmap(currentAddress));
        qrKeyView.setImageBitmap(Qr.bitmap(currentPrivKey));

        Toast.makeText(this, R.string.paper_wallet_generated, Toast.LENGTH_SHORT).show();
    }

    private void updatePrivKeyView() {
        if (keyVisible) {
            privKeyView.setText(currentPrivKey);
            toggleKeyButton.setText(R.string.paper_wallet_hide_key);
        } else {
            privKeyView.setText("••••••••••••••••••••••••••••••••••••••••");
            toggleKeyButton.setText(R.string.paper_wallet_show_key);
        }
    }

    private void toggleKeyVisibility() {
        keyVisible = !keyVisible;
        updatePrivKeyView();
    }

    private void copyText(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    private Bitmap renderCard() {
        Bitmap bitmap = Bitmap.createBitmap(cardView.getWidth(), cardView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        cardView.draw(canvas);
        return bitmap;
    }

    private File getShareFile() throws Exception {
        File dir = new File(getCacheDir(), "paperwallet");
        dir.mkdirs();
        File file = new File(dir, "paperwallet_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            renderCard().compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    private void savePaperWallet() {
        try {
            File file = getShareFile();
            Toast.makeText(this, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePaperWallet() {
        try {
            File file = getShareFile();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.paper_wallet_share)));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
