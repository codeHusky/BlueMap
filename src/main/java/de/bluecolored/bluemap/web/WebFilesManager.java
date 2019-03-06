/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.web;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;

public class WebFilesManager {

	private Path webRoot;
	
	public WebFilesManager(Path webRoot) {
		this.webRoot = webRoot;
	}
	
	public boolean needsUpdate() {
		if (!webRoot.resolve("index.html").toFile().exists()) return true;
	
		return false;
	}
	
	public void updateFiles() throws IOException {
		URL fileResource = getClass().getResource("/webroot.zip");
		File tempFile = File.createTempFile("bluemap_webroot_extraction", null);
		
		try {
			FileUtils.copyURLToFile(fileResource, tempFile, 10000, 10000);
			try (ZipFile zipFile = new ZipFile(tempFile)){
				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				while(entries.hasMoreElements()) {
					ZipEntry zipEntry = entries.nextElement();
					if (zipEntry.isDirectory()) webRoot.resolve(zipEntry.getName()).toFile().mkdirs();
					else {
						File target = webRoot.resolve(zipEntry.getName()).toFile();
						FileUtils.copyInputStreamToFile(zipFile.getInputStream(zipEntry), target);
					}
				}
			}
		} finally {
			tempFile.delete();
		}
	}
	
}
