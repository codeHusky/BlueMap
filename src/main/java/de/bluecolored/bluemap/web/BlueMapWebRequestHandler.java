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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import de.bluecolored.bluemap.webserver.HttpRequest;
import de.bluecolored.bluemap.webserver.HttpRequestHandler;
import de.bluecolored.bluemap.webserver.HttpResponse;
import de.bluecolored.bluemap.webserver.HttpStatusCode;

public class BlueMapWebRequestHandler implements HttpRequestHandler {

	private static final long DEFLATE_MIN_SIZE = 10L * 1024L;
	private static final long DEFLATE_MAX_SIZE = 10L * 1024L * 1024L;
	private static final long INFLATE_MAX_SIZE = 10L * 1024L * 1024L;
	
	private Path webRoot;
	
	public BlueMapWebRequestHandler(Path webRoot) {
		this.webRoot = webRoot;
	}
	
	@Override
	public HttpResponse handle(HttpRequest request) {
		if (
			!request.getMethod().equalsIgnoreCase("GET") &&
			!request.getMethod().equalsIgnoreCase("POST") 
		) return new HttpResponse(HttpStatusCode.NOT_IMPLEMENTED); 
		
		HttpResponse response = generateResponse(request);
		response.addHeader("Server", "BlueMap/WebServer");
		
		HttpStatusCode status = response.getStatusCode();
		if (status.getCode() >= 400){
			response.setData(status.getCode() + " - " + status.getMessage() + "\nBlueMap/Webserver");
		}
		
		return response;
	}

	private HttpResponse generateResponse(HttpRequest request) {
		String adress = request.getPath();
		if (adress.isEmpty()) adress = "/";
		String[] adressParts = adress.split("\\?", 2);
		String path = adressParts[0];
		String getParamString = adressParts.length > 1 ? adressParts[1] : ""; 
		
		Map<String, String> getParams = new HashMap<>();
		for (String getParam : getParamString.split("&")){
			if (getParam.isEmpty()) continue;
			String[] kv = getParam.split("=", 2);
			String key = kv[0];
			String value = kv.length > 1 ? kv[1] : "";
			getParams.put(key, value);
		}
		
		if (path.startsWith("/")) path = path.substring(1);
		if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
		
		Path filePath = webRoot;
		try {
			filePath = webRoot.resolve(path);
		} catch (InvalidPathException e){
			return new HttpResponse(HttpStatusCode.NOT_FOUND);
		}
		
		//can we use deflation?
		boolean isDeflationPossible = request.getLowercaseHeader("Accept-Encoding").contains("gzip");
		boolean isDeflated = false;
		
		//check if file is in web-root
		if (!filePath.normalize().startsWith(webRoot.normalize())){
			return new HttpResponse(HttpStatusCode.FORBIDDEN);
		}
		
		File file = filePath.toFile();
		
		if (!file.exists() || file.isDirectory()){
			file = new File(filePath.toString() + ".gz");
			isDeflated = true;
		}
		
		if (!file.exists() || file.isDirectory()){
			file = new File(filePath.toString() + "/index.html");
			isDeflated = false;
		}
		
		if (!file.exists() || file.isDirectory()){
			file = new File(filePath.toString() + "/index.html.gz");
			isDeflated = true;
		}
		
		if (!file.exists()){
			return new HttpResponse(HttpStatusCode.NOT_FOUND);
		}
		
		if (isDeflationPossible && (!file.getName().endsWith(".gz"))){
			File deflatedFile = new File(file.getAbsolutePath() + ".gz");
			if (deflatedFile.exists()){
				file = deflatedFile;
				isDeflated = true;
			}
		}
		
		//check if file is still in web-root
		if (!file.toPath().normalize().startsWith(webRoot.normalize())){
			return new HttpResponse(HttpStatusCode.FORBIDDEN);
		}

		//check modified
		long lastModified = file.lastModified();
		Set<String> modStringSet = request.getHeader("If-Modified-Since");
		if (!modStringSet.isEmpty()){
			try {
				long since = stringToTimestamp(modStringSet.iterator().next());
				if (since + 1000 >= lastModified){
					return new HttpResponse(HttpStatusCode.NOT_MODIFIED);
				}
			} catch (IllegalArgumentException e){}
		}
		

		HttpResponse response = new HttpResponse(HttpStatusCode.OK);
		if (lastModified > 0) response.addHeader("Last-Modified", timestampToString(lastModified));
		
		try {
			
			if (isDeflated){
				if (isDeflationPossible || file.length() > INFLATE_MAX_SIZE){
					response.addHeader("Content-Encoding", "gzip");
					response.setData(new FileInputStream(file));
					return response;
				} else {
					response.setData(new GZIPInputStream(new FileInputStream(file)));
					return response;
				}
			} else {
				if (isDeflationPossible && file.length() > DEFLATE_MIN_SIZE && file.length() < DEFLATE_MAX_SIZE){
					FileInputStream fis = new FileInputStream(file);
					ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
					GZIPOutputStream zos = new GZIPOutputStream(byteOut);
					IOUtils.copyLarge(fis, zos);
					zos.close();
					fis.close();
					byte[] compressedData = byteOut.toByteArray();
					response.setData(new ByteArrayInputStream(compressedData));
					response.addHeader("Content-Encoding", "gzip");
					return response;
				} else {
					response.setData(new FileInputStream(file));
					return response;
				}
			}
			
		} catch (FileNotFoundException e) {
			return new HttpResponse(HttpStatusCode.NOT_FOUND);
		} catch (IOException e) {
			return new HttpResponse(HttpStatusCode.INTERNAL_SERVER_ERROR);
		}
	}
	
	private static String timestampToString(long time){
		return DateFormatUtils.format(time, "EEE, dd MMM yyy HH:mm:ss 'GMT'", TimeZone.getTimeZone("GMT"), Locale.ENGLISH);
	}
	
	private static long stringToTimestamp(String timeString) throws IllegalArgumentException {
		try {
			int day = Integer.parseInt(timeString.substring(5, 7));
			int month = 1;
			switch (timeString.substring(8, 11)){
			case "Jan" : month = 0;  break;
			case "Feb" : month = 1;  break;
			case "Mar" : month = 2;  break;
			case "Apr" : month = 3;  break;
			case "May" : month = 4;  break;
			case "Jun" : month = 5;  break;
			case "Jul" : month = 6;  break;
			case "Aug" : month = 7;  break;
			case "Sep" : month = 8;  break;
			case "Oct" : month = 9; break;
			case "Nov" : month = 10; break;
			case "Dec" : month = 11; break;
			}
			int year = Integer.parseInt(timeString.substring(12, 16));
			int hour = Integer.parseInt(timeString.substring(17, 19));
			int min = Integer.parseInt(timeString.substring(20, 22));
			int sec = Integer.parseInt(timeString.substring(23, 25));
			GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
			cal.set(year, month, day, hour, min, sec);
			return cal.getTimeInMillis();
		} catch (NumberFormatException | IndexOutOfBoundsException e){
			throw new IllegalArgumentException(e);
		}
	}
	
}
