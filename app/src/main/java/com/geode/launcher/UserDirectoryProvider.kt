package com.geode.launcher

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.geode.launcher.utils.LaunchUtils
import java.io.File

private const val ROOT = "root"

private val DEFAULT_ROOT_PROJECTION: Array<String> = arrayOf(
    DocumentsContract.Root.COLUMN_ROOT_ID,
    DocumentsContract.Root.COLUMN_FLAGS,
    DocumentsContract.Root.COLUMN_ICON,
    DocumentsContract.Root.COLUMN_TITLE,
    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
)

private val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    DocumentsContract.Document.COLUMN_FLAGS,
    DocumentsContract.Document.COLUMN_SIZE
)

class UserDirectoryProvider : DocumentsProvider() {
    private lateinit var rootDir: File

    override fun onCreate(): Boolean {
        val context = context ?: return false
        rootDir = LaunchUtils.getBaseDirectory(context)

        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        // assert context is nonnull
        val context = context ?: return result

        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT)
            add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                        DocumentsContract.Root.FLAG_SUPPORTS_RECENTS or
                        DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
            )
            add(DocumentsContract.Root.COLUMN_TITLE, context.getString(R.string.app_name))
            add(DocumentsContract.Root.COLUMN_ICON, R.drawable.geode_logo)
        }

        return result
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        return MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
            val file = getFileForDocumentId(documentId ?: ROOT)
            appendDocument(this, file)
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        return MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
            val parent = getFileForDocumentId(parentDocumentId ?: ROOT)
            parent.listFiles()?.forEach { file -> appendDocument(this, file) }
        }
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocumentId(documentId ?: ROOT)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    private fun addDocument(cursor: MatrixCursor, file: File) {
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, getDocumentIdForFile(file))
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, typeForFile(file))
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        }
    }

    private fun appendDocument(cursor: MatrixCursor, file: File) {
        var flags = 0
        if (file.canWrite()) {
            flags = if (file.isDirectory) {
                DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            } else {
                DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            }
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            // The system will handle copy + move for us
        }

        val name = if (file == rootDir) {
            context!!.getString(R.string.app_name)
        } else {
            file.name
        }
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, getDocumentIdForFile(file))
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, typeForFile(file))
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            add(DocumentsContract.Document.COLUMN_SIZE, file.length())
            if (file == rootDir) {
                add(DocumentsContract.Document.COLUMN_ICON, R.drawable.geode_logo)
            }
        }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String? {
        val folder = getFileForDocumentId(parentDocumentId)
        val file = findFileNameForNewFile(File(folder, displayName))
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            file.mkdirs()
        } else {
            file.createNewFile()
        }

        return getDocumentIdForFile(file)
    }

     override fun deleteDocument(documentId: String) {
        val file = getFileForDocumentId(documentId)
        file.deleteRecursively()
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        val file = getFileForDocumentId(documentId)
        val dest = findFileNameForNewFile(File(file.parentFile, displayName))
        file.renameTo(dest)
        return getDocumentIdForFile(dest)
    }

    private fun typeForFile(file: File): String {
        if (file.isDirectory) {
            return DocumentsContract.Document.MIME_TYPE_DIR
        }

        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
    }

    private fun getFileForDocumentId(documentId: String): File {
        return File(rootDir, documentId.removePrefix(ROOT))
    }

    private fun getDocumentIdForFile(file: File): String {
        return ROOT + file.toRelativeString(rootDir)
    }

    private fun findFileNameForNewFile(file: File): File {
        var unusedFile = file
        var i = 1
        while (unusedFile.exists()) {
            val pathWithoutExtension = unusedFile.absolutePath.substringBeforeLast('.')
            val extension = unusedFile.absolutePath.substringAfterLast('.')
            unusedFile = File("$pathWithoutExtension.$i.$extension")
            i++
        }
        return unusedFile
    }
}