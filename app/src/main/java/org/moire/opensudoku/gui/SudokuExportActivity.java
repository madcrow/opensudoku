package org.moire.opensudoku.gui;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.format.DateFormat;
import android.util.Log;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.moire.opensudoku.R;
import org.moire.opensudoku.db.SudokuDatabase;
import org.moire.opensudoku.game.FolderInfo;
import org.moire.opensudoku.gui.exporting.FileExportTask;
import org.moire.opensudoku.gui.exporting.FileExportTaskParams;

import java.io.File;
import java.util.Date;

public class SudokuExportActivity extends ThemedActivity {
    /**
     * Id of folder to export. If -1, all folders will be exported.
     */
    public static final String EXTRA_FOLDER_ID = "FOLDER_ID";
    /**
     * Id of sudoku to export.
     */
//	public static final String EXTRA_SUDOKU_ID = "SUDOKU_ID";

    private int STORAGE_PERMISSION_CODE = 1;

    public static final long ALL_FOLDERS = -1;

    private static final int DIALOG_FILE_EXISTS = 1;
    private static final int DIALOG_PROGRESS = 2;

    private static final String TAG = SudokuExportActivity.class.getSimpleName();

    private FileExportTask mFileExportTask;
    private FileExportTaskParams mExportParams;

    private EditText mFileNameEdit;
    private EditText mDirectoryEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sudoku_export);

        mFileNameEdit = findViewById(R.id.filename);
        mDirectoryEdit = findViewById(R.id.directory);
        Button mSaveButton = findViewById(R.id.save_button);
        mSaveButton.setOnClickListener(mOnSaveClickListener);

        mFileExportTask = new FileExportTask(this);
        mExportParams = new FileExportTaskParams();

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_FOLDER_ID)) {
            mExportParams.folderID = intent.getLongExtra(EXTRA_FOLDER_ID, ALL_FOLDERS);
//		} else if (intent.hasExtra(EXTRA_SUDOKU_ID)) {
//			mExportParams.sudokuID = intent.getLongExtra(EXTRA_SUDOKU_ID, 0);
        } else {
            Log.d(TAG, "No 'FOLDER_ID' extra provided, exiting.");
            finish();
            return;
        }

        String fileName;
        String timestamp = DateFormat.format("yyyy-MM-dd", new Date()).toString();
        if (mExportParams.folderID == -1) {
            fileName = "all-folders-" + timestamp;
        } else {
            SudokuDatabase database = new SudokuDatabase(getApplicationContext());
            FolderInfo folder = database.getFolderInfo(mExportParams.folderID);
            if (folder == null) {
                Log.d(TAG, String.format("Folder with id %s not found, exiting.", mExportParams.folderID));
                finish();
                return;
            }
            fileName = folder.name + "-" + timestamp;
            database.close();
        }
        mFileNameEdit.setText(fileName);


        //showDialog(DIALOG_SELECT_EXPORT_METHOD);
    }

    private OnClickListener mOnSaveClickListener = v -> exportToFile();

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_FILE_EXISTS:

                return new AlertDialog.Builder(this)
                        .setTitle(R.string.export)
                        .setMessage(R.string.file_exists)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> startExportToFileTask())
                        .setNegativeButton(android.R.string.no, null)
                        .create();
            case DIALOG_PROGRESS:
                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setIndeterminate(true);
                progressDialog.setTitle(R.string.app_name);
                progressDialog.setMessage(getString(R.string.exporting));
                return progressDialog;
        }

        return null;
    }

    private void exportToFile() {
        // check for permission to write to sd card
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission();
        }
        else {
            File sdcard = new File("/sdcard");
            if (!sdcard.exists()) {
                Toast.makeText(SudokuExportActivity.this, R.string.sdcard_not_found, Toast.LENGTH_LONG).show();
                finish();
            }

            String directory = mDirectoryEdit.getText().toString();
            String filename = mFileNameEdit.getText().toString();

            File file = new File(directory, filename + ".opensudoku");
            if (file.exists()) {
                showDialog(DIALOG_FILE_EXISTS);
            } else {
                startExportToFileTask();
            }
        }
    }

    private void startExportToFileTask() {
        mFileExportTask.setOnExportFinishedListener(result -> {
            dismissDialog(DIALOG_PROGRESS);

            if (result.successful) {
                Toast.makeText(SudokuExportActivity.this, getString(
                        R.string.puzzles_have_been_exported, result.file), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SudokuExportActivity.this, getString(
                        R.string.unknown_export_error), Toast.LENGTH_LONG).show();
            }
            finish();
        });

        String directory = mDirectoryEdit.getText().toString();
        String filename = mFileNameEdit.getText().toString();

        mExportParams.file = new File(directory, filename + ".opensudoku");

        showDialog(DIALOG_PROGRESS);
        mFileExportTask.execute(mExportParams);
    }

    private void requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("This permission is needed in order to access your SD Card")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(SudokuExportActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
                        }
                    })
                    .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportToFile();
            }
            else {
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show();
            }
        }
    }
/*
    private void exportToMail() {

        mFileExportTask.setOnExportFinishedListener(result -> {
            mProgressDialog.dismiss();

            if (result.successful) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Const.MIME_TYPE_OPENSUDOKU);
                intent.putExtra(Intent.EXTRA_TEXT, "Puzzles attached.");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(result.file));

                try {
                    startActivity(intent);
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(SudokuExportActivity.this, "no way to share folder", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(SudokuExportActivity.this, "not successful.", Toast.LENGTH_LONG).show();
            }

            finish();
        });

        mProgressDialog.show();
        mFileExportTask.execute(mExportParams);
    }
*/

}
