/*
 * Copyright (c) 2015 FUJI Goro (gfx).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.gfx.android.orma.migration;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import java.util.List;

@SuppressLint("Assert")
public class ManualStepMigration implements MigrationEngine {

    public static final String TAG = "ManualStepMigration";

    public static final String MIGRATION_STEPS_TABLE = "orma_migration_steps";

    static final String MIGRATION_HISTORY_DDL = "CREATE TABLE IF NOT EXISTS "
            + MIGRATION_STEPS_TABLE + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "version INTEGER NOT NULL, "
            + "sql TEXT NULL, "
            + "created_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)";

    static final String kId = "id";

    static final String kVersion = "version";

    static final String kSql = "sql";

    final int version;

    final boolean trace;

    final SparseArray<Step> steps;

    boolean tableCreated = false;

    public ManualStepMigration(int version, boolean trace) {
        this.version = version;
        this.trace = trace;
        this.steps = new SparseArray<>(0);
    }

    public void addStep(int version, @NonNull ManualStepMigration.Step step) {
        steps.put(version, step);
    }

    @Override
    public int getVersion() {
        return version;
    }

    public int getDbVersion(SQLiteDatabase db) {
        Cursor cursor = db.query(MIGRATION_STEPS_TABLE, new String[]{kVersion},
                null, null, null, null, kId + " DESC", "1");
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                return 0;
            }
        } finally {
            cursor.close();
        }
    }

    @Override
    public void start(@NonNull SQLiteDatabase db, @NonNull List<? extends MigrationSchema> schemas) {
        createMigrationHistoryTable(db);

        int oldVersion = getDbVersion(db);

        Log.v(TAG, "Migration from " + oldVersion + " to " + version);

        if (oldVersion == 0) {
            Log.v(TAG, "Skip migration because there is no manual migration history");
            return;
        }

        if (oldVersion == version) {
            Log.v(TAG, "No need to run migration");
            return;
        }

        if (oldVersion < version) {
            upgrade(db, oldVersion, version);
        } else {
            downgrade(db, oldVersion, version);
        }
    }

    void createMigrationHistoryTable(SQLiteDatabase db) {
        if (!tableCreated) {
            db.execSQL(MIGRATION_HISTORY_DDL);
            tableCreated = true;
        }
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        assert oldVersion < newVersion;

        createMigrationHistoryTable(db);

        for (int i = 0, size = steps.size(); i < size; i++) {
            int version = steps.keyAt(i);
            if (oldVersion < version && version <= newVersion) {
                if (trace) {
                    Log.v(TAG, "upgrade step #" + version + " from " + oldVersion + " to " + newVersion);
                }
                Step step = steps.valueAt(i);
                step.up(new Helper(db, version, true));
            }
        }
    }

    public void downgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        assert oldVersion > newVersion;

        createMigrationHistoryTable(db);

        for (int i = steps.size() - 1; i >= 0; i--) {
            int version = steps.keyAt(i);
            if (newVersion < version && version <= oldVersion) {
                if (trace) {
                    Log.v(TAG, "downgrade step #" + version + " from " + oldVersion + " to " + newVersion);
                }
                Step step = steps.valueAt(i);
                step.down(new Helper(db, version, false));
            }
        }
    }

    public void saveStep(SQLiteDatabase db, int version, @Nullable String sql) {
        createMigrationHistoryTable(db);

        ContentValues values = new ContentValues();
        values.put(kVersion, version);
        values.put(kSql, sql);
        db.insertOrThrow(MIGRATION_STEPS_TABLE, null, values);
    }

    public void execStep(SQLiteDatabase db, int version, @Nullable String sql) {
        if (trace) {
            Log.v(TAG, sql);
        }
        db.execSQL(sql);
        saveStep(db, version, sql);
    }

    /**
     * A migration step which handles {@code down()}, and {@code change()}.
     */
    public interface Step {

        void up(@NonNull ManualStepMigration.Helper helper);

        void down(@NonNull ManualStepMigration.Helper helper);

    }

    /**
     * A migration step which handles {@code change()}.
     */
    public static abstract class ChangeStep implements Step {

        @Override
        public void up(@NonNull ManualStepMigration.Helper helper) {
            change(helper);
        }

        @Override
        public void down(@NonNull ManualStepMigration.Helper helper) {
            change(helper);
        }

        public abstract void change(@NonNull ManualStepMigration.Helper helper);
    }

    public class Helper {

        public final int version;

        public final boolean upgrade;

        final SQLiteDatabase db;

        final SqliteDdlBuilder sqliteDdlBuilder = new SqliteDdlBuilder();

        public Helper(SQLiteDatabase db, int version, boolean upgrade) {
            this.db = db;
            this.version = version;
            this.upgrade = upgrade;
        }

        public void renameTable(@NonNull String fromTableName, @NonNull String toTableName) {
            String sql;
            if (upgrade) {
                sql = sqliteDdlBuilder.buildRenameTable(fromTableName, toTableName);
            } else {
                sql = sqliteDdlBuilder.buildRenameTable(toTableName, fromTableName);
            }
            execSQL(sql);
        }

        public void execSQL(@NonNull String sql) {
            execStep(db, upgrade ? version : version - 1, sql);
        }
    }
}
