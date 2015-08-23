package org.owntracks.android.activities;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.owntracks.android.R;
import org.owntracks.android.messages.ConfigurationMessage;
import org.owntracks.android.services.ServiceProxy;
import org.owntracks.android.support.Preferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumSet;

public class ActivityImport extends ActivityBase {
    private static final String TAG = "ActivityImport";

    private TextView input;
    private MenuItem saveButton;
    JSONObject configJSON = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);
        Toolbar toolbar = (Toolbar) findViewById(R.id.fragmentToolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getResources().getString(R.string.importConfig));



        input = (TextView) findViewById(R.id.input);

        Log.v(TAG, "checking for import intent");
        // Look for Import Preference File Intent
        final Intent intent = getIntent();
        final String action = intent.getAction();
        Log.v(TAG, "action: " + intent.getAction());

        if(Intent.ACTION_VIEW.equals(action)) {
            Log.v(TAG, "action ok, getting data uri");

            Uri uri = intent.getData();
            Log.v(TAG, "uri: " + uri);

            if (uri != null) {
                extractPreferences(uri);

            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_export, menu);

        saveButton = menu.findItem(R.id.save);
        saveButton.setEnabled(configJSON != null);
        saveButton.getIcon().setAlpha(configJSON != null ? 255 : 130);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save:
                importAction();
                return true;
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    private void importAction() {
        Log.v(TAG, "Importing configuration. Brace for impact.");
        Preferences.fromJsonObject(configJSON);

       // importPreferenceResultDialog("Success", "Preferences import successful.\nIt is recommended to restart the app.");
        Snackbar s = Snackbar.make(findViewById(R.id.frame), R.string.snackbarImportCompleted, Snackbar.LENGTH_LONG);
        s.setAction("Restart", new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.e(TAG, "restarting app");
                Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
                PendingIntent intent = PendingIntent.getActivity(getApplicationContext(), 0, i, Intent.FLAG_ACTIVITY_CLEAR_TOP);
                AlarmManager manager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                manager.set(AlarmManager.RTC, System.currentTimeMillis() + 1, intent);
                System.exit(2);

            }
        });
        s.show();


    }

    private void extractPreferences(Uri uri){

        try{

             InputStream stream =  getContentResolver().openInputStream(uri);

            BufferedReader r = new BufferedReader(new InputStreamReader(stream));
            StringBuilder total = new StringBuilder();

            try {
                String content;
                while ((content = r.readLine()) != null) {
                    total.append(content);
                }
            } catch (OutOfMemoryError e) {
                throw new Error("Unable to load content into memory");
            }

            Log.v(TAG, "file content: " + total);


            if(total == null) {
                throw new Error("Unable to read content");
            }

            JSONObject j = new JSONObject(total.toString());
            if(j == null) {
                throw new Error("Unable to parse content");
            }


            //            Log.v(TAG, "parsing to JSON");
//            StringifiedJSONObject j = new StringifiedJSONObject(fileContent);

//            Log.v(TAG, "json: " + j.toString());


            configJSON = j;
            input.setText(formatString(j.toString()));
/*
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(getResources().getString(R.string.preferencesImportFile))
                    .setMessage(filePath)
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // ???
                        }
                    })
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {

                                Preferences.fromJsonObject(new StringifiedJSONObject(fileContent));
                                Runnable r = new Runnable() {
                                    @Override
                                    public void run() {
                                        ServiceProxy.getServiceBroker().reconnect();
                                    }
                                };
                                new Thread(r).start();

                            } catch (JSONException e) {
                                importPreferenceResultDialog("Preferences import failed!");
                            }

                            importPreferenceResultDialog("Preferences imported successfully !");
                        }
                    });

            Dialog dialog = builder.create();
            dialog.show();
*/

        }catch(Exception e){

            Log.e("Export", "importPreferenceDialog exception: " +e);
            importPreferenceResultDialog("Error", "Preferences import failed!");
        }

    }

    private void importPreferenceResultDialog(String title, String message){

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });

        Dialog dialog = builder.create();
        dialog.show();
    }

    private String readFile( String file ) throws IOException {

        BufferedReader reader = new BufferedReader( new FileReader (file));
        String         line = null;
        StringBuilder  stringBuilder = new StringBuilder();
        String         ls = System.getProperty("line.separator");

        while( ( line = reader.readLine() ) != null ) {
            stringBuilder.append( line );
            stringBuilder.append( ls );
        }

        reader.close();

        return stringBuilder.toString();
    }


    public static String formatString(String text){

        StringBuilder json = new StringBuilder();
        String indentString = "";

        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            switch (letter) {
                case '{':
                case '[':
                    json.append("\n" + indentString + letter + "\n");
                    indentString = indentString + "\t";
                    json.append(indentString);
                    break;
                case '}':
                case ']':
                    indentString = indentString.replaceFirst("\t", "");
                    json.append("\n" + indentString + letter);
                    break;
                case ',':
                    json.append(letter + "\n" + indentString);
                    break;

                default:
                    json.append(letter);
                    break;
            }
        }

        return json.toString();
    }

}
