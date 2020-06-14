package com.example.playerproject.manager

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.MimeTypeFilter

class FileManager(private val context: Context) {

    fun getFilesFromTreeUri(treeUri: Uri, mimeType: String): List<Uri>? {
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

        val resolver = context.contentResolver
        var cursor: Cursor? = null
        val resultList = mutableListOf<Uri>()
        try {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
            cursor = resolver.query(uri, projection, null, null, null, null)
            while (cursor?.moveToNext() == true) {
                if (MimeTypeFilter.matches(cursor.getString(1), mimeType)) {
                    val documentId = cursor.getString(0)
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    resultList.add(documentUri)
                }
            }
        } catch (e: Exception) {
            return null
        } finally {
            cursor?.close()
        }
        return resultList
    }

    fun getAudioFilesFromTreeUri(treeUri: Uri): List<Uri>? {
        return getFilesFromTreeUri(treeUri, "audio/*")
    }
}
