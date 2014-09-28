package com.example.conor.couchdb;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import com.couchbase.lite.*;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.util.Log;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class MyActivity extends Activity {
    private final String DB_NAME = "music", APP_NAME = "CouchDB", VIEW_NAME="albums";
    private TextView _display;
    private Button _btn;
    private Document _document, _retrievedDocument;
    private Database _database;
    private Manager _manager;
    private com.couchbase.lite.View _albums;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        initializeControls();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeControls() {
        _display = (TextView) findViewById(R.id.display);
        _display.setMovementMethod(new ScrollingMovementMethod());
        _btn = (Button) findViewById(R.id.btn);
        setButton("Create Document");
    }

    private Manager createManager(){
        Manager manager = null;
        try {
            manager = new Manager(new AndroidContext(this), Manager.DEFAULT_OPTIONS);
        } catch (IOException e) {
            Log.e(APP_NAME, "Cannot create manager object");
            return null;
        }
        if (!manager.isValidDatabaseName(DB_NAME)) {
            Log.e(APP_NAME, "Bad database name '" + DB_NAME + "'");
            return null;
        }
        return manager;
    }

    private Database createDatabase(Manager manager) {
        Database database = null;
        try {
            database = manager.getDatabase(DB_NAME);

        } catch (CouchbaseLiteException e) {
            Log.e(APP_NAME, "Cannot get database");
            return null;
        }
        return database;
    }

    private void createView() {
        _albums = _database.getView(VIEW_NAME);
        if (_albums.getMap() == null) {
            _albums.setMapReduce(new Mapper() {
                @Override
                public void map(Map<String, Object> document, Emitter emitter) {
                    Object o = document.get("albums");
                    String getAlbums = o.toString();
                    String[] albums = getAlbums.split(",");
                    for(String album : albums){
                        emitter.emit(document.get("name"), 1);
                    }
                }
            }, new Reducer() {
                @Override
                public Object reduce(List<Object> keys, List<Object> values, boolean reReduce) {
                    return "\n\nBand - " + keys.get(0) + " | No. Albums - " + values.size();
                }
            },
            "1");
        }
    }

    private Document createDocument() {
        _manager = createManager();
        if(_manager != null){
            _database = createDatabase(_manager);
        }
        Map<String, Object> band = new HashMap<String, Object>();
        band.put("name", "U2");
        Document document = _database.createDocument();
        try {
            document.putProperties(band);
        } catch (CouchbaseLiteException e) {
            Log.e(APP_NAME, "Cannot write document to database", e);
            return null;
        }
        return document;
    }

    private Document retrieveDocument(){
        return _database.getDocument(_document.getId());
    }

    private void updateDocument() {
        Map<String, Object> band = new HashMap<String, Object>();
        band.putAll(_retrievedDocument.getProperties());
        String[] albums = {"The Joshua Tree", "War", "Achtung Baby"};
        band.put("albums", Arrays.toString(albums));
        try {
            _retrievedDocument.putProperties(band);
        } catch (CouchbaseLiteException e) {
            Log.e(APP_NAME, "Cannot update document", e);
        }
        createView();
    }

    private void deleteDocument() {
        try {
            _retrievedDocument.delete();
        } catch (CouchbaseLiteException e) {
            Log.e(APP_NAME, "Cannot delete document", e);
        }
    }

    private void runQueryOnView() {
        Query query = _albums.createQuery();
        QueryEnumerator result = null;
        try{
            result = query.run();
        }catch (Exception e){
            Log.e(APP_NAME, "Query Failed", e.toString());
            return;
        }
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();
            _display.append("\n\nMAPREDUCE" + row.getValue());
        }
    }

    private void displayProperties() {
        for(Map.Entry<String, Object> property : _retrievedDocument.getProperties().entrySet()){
            _display.append("\n" + property.getKey() + " - " + property.getValue());
        }
    }

    private void setButton(final String buttonText) {
        _btn.setText(buttonText);

        _btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (buttonText.startsWith("Create")) {
                    _document = createDocument();
                    _display.setText("CREATE\n\nDocument " + _document.getId() + " has been created!");
                    setButton("Retrieve Document");
                } else if (buttonText.startsWith("Retrieve")) {
                    _retrievedDocument = retrieveDocument();
                    _display.append("\n\nREAD\n");
                    displayProperties();
                    setButton("Update Document");
                } else if (buttonText.startsWith("Update")) {
                    updateDocument();
                    _display.append("\n\nUPDATE\n");
                    displayProperties();
                    setButton("Query using Mapreduce");
                }   else if (buttonText.startsWith("Query")) {
                    runQueryOnView();
                    setButton("Delete Document");
                } else if (buttonText.startsWith("Delete")) {
                    deleteDocument();
                    _display.append("\n\nDELETE\n\nDocument " + _retrievedDocument.getId() + " deleted.");
                    setButton("Clear");
                } else {
                    try{
                        _database.deleteViewNamed(VIEW_NAME);
                        _database.delete();
                    }catch(Exception e){
                        Log.e(APP_NAME, "View or Database deletion failed", e.toString());
                    }
                    _display.setText("");
                    setButton("Create");
                }
            }
        });
    }
}
