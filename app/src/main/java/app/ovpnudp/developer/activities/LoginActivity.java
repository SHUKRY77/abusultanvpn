package app.ovpnudp.developer.activities;

import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import app.ovpnudp.developer.utils.ExpireDate;
import com.yemennet.vpn.R;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener, ExpireDate.ExpireDateListener {
    private EditText username;
    private EditText password;
    private Button loginButton;
    private TextView status;
    private SharedPreferences prefs;
    private final Handler loginHandler = new Handler(Looper.getMainLooper());
    private boolean loginInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        username = findViewById(R.id.login_username);
        password = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        status = findViewById(R.id.login_status);
        username.setText(prefs.getString(OpenVPNClient.USERNAME, ""));
        if (prefs.getBoolean("isLogin", false) && !username.getText().toString().isEmpty()) {
            openClient();
            return;
        }
        loginButton.setOnClickListener(this);
    }

    @Override public void onClick(View view) { doLogin(); }

    private void doLogin() {
        final String user = username.getText().toString().trim();
        final String pass = password.getText().toString();
        if (user.isEmpty() || pass.isEmpty()) {
            showError("Please enter username and password");
            return;
        }
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            showError("No Internet Connection");
            return;
        }
        loginButton.setEnabled(false);
        loginInProgress = true;
        loginButton.setText("SIGNING IN...");
        status.setText("Verifying account...");
        status.setTextColor(0xFF90A4AE);
        status.setVisibility(View.VISIBLE);
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null) deviceId = "unknown";
        String url = "https://abusultanvpn.site/api/auth.php?username=" + Uri.encode(user)
                + "&password=" + Uri.encode(pass)
                + "&device_id=" + Uri.encode(deviceId)
                + "&device_model=" + Uri.encode(Build.MODEL);
        ExpireDate request = new ExpireDate();
        request.setUrl(url);
        request.setExpireDateListener(this);
        request.start();
        loginHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (loginInProgress) {
                    loginInProgress = false;
                    showError("Server timeout. Please try again");
                }
            }
        }, 15000);
    }

    private void showError(String message) {
        loginInProgress = false;
        loginButton.setEnabled(true);
        loginButton.setText("SIGN IN  ›");
        status.setTextColor(0xFFFF5252);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        Snackbar.make(loginButton, message, Snackbar.LENGTH_SHORT).show();
    }

    private void openClient() {
        startActivity(new Intent(this, OpenVPNClient.class));
        finish();
    }

    @Override public void onExpireDate(String expiry) {
        if (!loginInProgress) return;
        loginInProgress = false;
        prefs.edit().putString(OpenVPNClient.USERNAME, username.getText().toString().trim())
                .putString(OpenVPNClient.PASSWORD, password.getText().toString())
                .putString("ExpireDate", expiry)
                .putBoolean("isLogin", true).apply();
        openClient();
    }
    @Override public void onDeviceNotMatch(String message) { if (loginInProgress) showError(message); }
    @Override public void onAuthFailed(String message) { if (loginInProgress) showError("Authentication Failed"); }
    @Override public void onError(String error) { if (loginInProgress) showError("Login failed. Please try again"); }
}
