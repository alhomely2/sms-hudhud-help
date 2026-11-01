package com.hudhud.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TemplateEntity::class,
        CampaignEntity::class,
        CampaignMessageEntity::class,
        ImportBatchEntity::class,
        ImportMappingEntity::class,
        OutgoingMessageEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun campaignDao(): CampaignDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun importMappingDao(): ImportMappingDao
    abstract fun outgoingMessageDao(): OutgoingMessageDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS campaigns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        status TEXT NOT NULL,
                        totalMessages INTEGER NOT NULL,
                        validMessages INTEGER NOT NULL,
                        reviewMessages INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS campaign_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        campaignId INTEGER NOT NULL,
                        sourceRow INTEGER NOT NULL,
                        phone TEXT NOT NULL,
                        text TEXT NOT NULL,
                        status TEXT NOT NULL,
                        errors TEXT NOT NULL,
                        FOREIGN KEY(campaignId) REFERENCES campaigns(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_campaign_messages_campaignId ON campaign_messages(campaignId)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE templates ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL("UPDATE templates SET updatedAt = createdAt WHERE updatedAt = 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE templates ADD COLUMN fixedValuesJson TEXT NOT NULL DEFAULT '{}'",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create import_batches table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_batches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        fileUri TEXT NOT NULL,
                        importMode TEXT NOT NULL DEFAULT 'RAW_DATA',
                        templateId INTEGER,
                        templateName TEXT,
                        templateBodySnapshot TEXT,
                        templateVariablesSnapshot TEXT,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        totalRows INTEGER NOT NULL DEFAULT 0,
                        validRows INTEGER NOT NULL DEFAULT 0,
                        errorRows INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent(),
                )

                // Create import_mappings table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_mappings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        batchId INTEGER NOT NULL,
                        phoneColumn TEXT NOT NULL,
                        variableMappingsJson TEXT NOT NULL DEFAULT '{}',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isComplete INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(batchId) REFERENCES import_batches(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )

                // Create index for import_mappings
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_import_mappings_batchId ON import_mappings(batchId)",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Templates additions
                database.execSQL("ALTER TABLE templates ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'USER'")
                database.execSQL("ALTER TABLE templates ADD COLUMN sourceVersion INTEGER NOT NULL DEFAULT 1")

                // Campaigns additions
                database.execSQL("ALTER TABLE campaigns ADD COLUMN importMode TEXT NOT NULL DEFAULT 'RAW_DATA'")
                database.execSQL("ALTER TABLE campaigns ADD COLUMN batchId INTEGER")

                // Campaign messages additions
                database.execSQL("ALTER TABLE campaign_messages ADD COLUMN batchId INTEGER")
                database.execSQL("ALTER TABLE campaign_messages ADD COLUMN sendStatus TEXT NOT NULL DEFAULT 'PREPARED'")
                database.execSQL("ALTER TABLE campaign_messages ADD COLUMN platformRef TEXT")
                database.execSQL("ALTER TABLE campaign_messages ADD COLUMN sentAt INTEGER")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_campaign_messages_batchId ON campaign_messages(batchId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_campaign_messages_sendStatus ON campaign_messages(sendStatus)")

                // Outgoing messages table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS outgoing_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        campaignId INTEGER NOT NULL,
                        messageId INTEGER NOT NULL,
                        siteProfileId TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        maxRetryAttempts INTEGER NOT NULL DEFAULT 3,
                        payloadJson TEXT NOT NULL DEFAULT '',
                        lastError TEXT NOT NULL DEFAULT '',
                        correlationId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastAttemptAt INTEGER,
                        FOREIGN KEY(campaignId) REFERENCES campaigns(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_outgoing_messages_campaignId ON outgoing_messages(campaignId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_outgoing_messages_site_profile_status ON outgoing_messages(siteProfileId, status)",
                )
            }
        }

        // Migration from version 6 to 7 - New template fields
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new template columns
                database.execSQL("ALTER TABLE templates ADD COLUMN selectedFieldsJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE templates ADD COLUMN fixedDataJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE templates ADD COLUMN sourceFileName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE templates ADD COLUMN sourceFilePath TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE templates ADD COLUMN documentHeadersJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE templates ADD COLUMN documentSampleJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        private val DATABASE_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // As per specification: on app startup, convert stuck SENDING messages to UNKNOWN
                db.execSQL("UPDATE campaign_messages SET sendStatus = 'UNKNOWN' WHERE sendStatus = 'SENDING'")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hudhud.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                ).addCallback(DATABASE_CALLBACK).build().also { instance = it }
            }
    }
}
