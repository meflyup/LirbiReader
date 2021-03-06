package org.ebookdroid.droids;

import java.io.File;

import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.droids.mupdf.codec.MuPdfDocument;
import org.ebookdroid.droids.mupdf.codec.PdfContext;

import com.foobnix.android.utils.LOG;
import com.foobnix.ext.CacheZipUtils;

import junrar.ExtractArchive;

public class CbrContext extends PdfContext {

	File cacheFile;

	@Override
	public File getCacheFileName(String fileNameOriginal) {
		cacheFile = new File(CacheZipUtils.CACHE_BOOK_DIR, fileNameOriginal.hashCode() + ".cbz");
		return cacheFile;
	}

	@Override
	public CodecDocument openDocumentInner(String fileName, String password) {

		if (!cacheFile.isFile()) {

			try {
				String extractDir = CacheZipUtils.CACHE_BOOK_DIR + "/" + "CBR_" + fileName.hashCode();

				File cbrDir = new File(extractDir);
				cbrDir.mkdirs();

				ExtractArchive.extractArchive(fileName, extractDir, password);
				CacheZipUtils.zipFolder(extractDir, cacheFile.getPath());

				CacheZipUtils.deleteDir(cbrDir);

			} catch (Exception e) {
				LOG.e(e);
			}
		}

		MuPdfDocument muPdfDocument = new MuPdfDocument(this, MuPdfDocument.FORMAT_PDF, cacheFile.getPath(), password);
		return muPdfDocument;
	}

}
