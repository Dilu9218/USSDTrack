package inc.padmal.ussdtrack;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    LineChart chartMoney, chartData;
    String ussd = "*100" + Uri.encode("#");
    Plotter plotter;
    TextView tvData, tvMoney;
    SharedPreferences Logs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        chartMoney = findViewById(R.id.chartMoney);
        chartData = findViewById(R.id.chartData);

        tvData = findViewById(R.id.tv_data);
        tvMoney = findViewById(R.id.tv_money);

        plotter = Plotter.getInstance();

        Logs = getSharedPreferences("USSD Tracker", MODE_PRIVATE);

        startService(new Intent(getApplicationContext(), USSDService.class));

        LocalBroadcastManager.getInstance(this).registerReceiver(USSDReceiver,
                new IntentFilter("com.times.ussd.action.REFRESH"));

        SMSReceiver.bindListener(new SmsListener() {
            @Override
            public void messageReceived(String messageText) {
                processData(Logs.getString("DATA", ""),
                        messageText, chartData, "DATA", "Data", Color.RED, tvData, false);
            }
        });

        makeTheCall();

        plotter.loadCharts(getApplicationContext(), chartMoney,
                Logs.getString("MONEY", ""), Color.BLUE, "Money", tvMoney, true);
        plotter.loadCharts(getApplicationContext(), chartData,
                Logs.getString("DATA", ""), Color.RED, "Data", tvData, false);
    }

    private void makeTheCall() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + ussd)));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_review:
                makeTheCall();
                break;
            case R.id.action_clean:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder
                        .setMessage("You are going to delete all the chart data. Are you sure?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Logs.edit().clear().apply();
                                chartMoney.invalidate();
                                chartMoney.clear();
                                chartData.invalidate();
                                chartData.clear();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {/**/}
                        })
                        .create().show();
                break;
            default:
                break;
        }
        super.onOptionsItemSelected(item);
        return true;
    }

    private BroadcastReceiver USSDReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("USSD");
            processData(Logs.getString("MONEY", ""),
                    message.substring(message.indexOf("Rs.") + 4, message.indexOf("will") - 1),
                    chartMoney, "MONEY", "Money", Color.BLUE, tvMoney, true);
        }
    };

    private void processData(String JStr, String msg, LineChart chart, String Key, String label,
                             int color, TextView tv, boolean mode) {
        try {
            JSONObject jsonObject = new JSONObject(JStr);
            jsonObject.put(String.valueOf(System.currentTimeMillis()), msg);
            String updateJSONString = jsonObject.toString();
            Logs.edit().putString(Key, updateJSONString).apply();
            plotter.plotChart(getApplicationContext(), jsonObject, chart, label,
                    color, tv, mode);
        } catch (JSONException e) {
            JSONObject FirstJSON = new JSONObject();
            try {
                FirstJSON.put(String.valueOf(System.currentTimeMillis()), msg);
                String updateJSONString = FirstJSON.toString();
                Logs.edit().putString(Key, updateJSONString).apply();
                plotter.plotChart(getApplicationContext(), FirstJSON, chart, label,
                        color, tv, mode);
            } catch (JSONException j) {
                Toast.makeText(getApplicationContext(), "Error in parsing", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(USSDReceiver);
        super.onDestroy();
    }
}
