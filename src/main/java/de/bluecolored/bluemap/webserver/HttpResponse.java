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
package de.bluecolored.bluemap.webserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

public class HttpResponse {

	private String version;
	private HttpStatusCode statusCode;
	private Map<String, Set<String>> header;
	private InputStream data;
	
	public HttpResponse(HttpStatusCode statusCode) {
		this.version = "HTTP/1.1";
		this.statusCode = statusCode;
		
		this.header = new HashMap<>();

		addHeader("Connection", "keep-alive");
	}
	
	public void addHeader(String key, String value){
		Set<String> valueSet = header.get(key);
		if (valueSet == null){
			valueSet = new HashSet<>();
			header.put(key, valueSet);
		}
		
		valueSet.add(value);
	}

	public void removeHeader(String key, String value){
		Set<String> valueSet = header.get(key);
		if (valueSet == null){
			valueSet = new HashSet<>();
			header.put(key, valueSet);
		}
		
		valueSet.remove(value);
	}
	
	public void setData(InputStream dataStream){
		this.data = dataStream;
	}
	
	public void setData(String data){
		setData(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
	}
	
	/**
	 * Writes this Response to an Output-Stream.<br>
	 * <br>
	 * This method closes the data-Stream of this response so it can't be used again!
	 */
	public void write(OutputStream out) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

		if (data != null){
			addHeader("Transfer-Encoding", "chunked");
		} else {
			addHeader("Content-Length", "0");
		}
		
		writeLine(writer, version + " " + statusCode.getCode() + " " + statusCode.getMessage());
		for (Entry<String, Set<String>> e : header.entrySet()){
			if (e.getValue().isEmpty()) continue;
			writeLine(writer, e.getKey() + ": " + StringUtils.join(e.getValue(), ", "));
		}
		
		writeLine(writer, "");
		writer.flush();

		if(data != null){
			chunkedPipe(data, out);
			out.flush();
			data.close();
		}
	}
	
	private void writeLine(OutputStreamWriter writer, String line) throws IOException {
		writer.write(line + "\r\n");
	}
	
	private void chunkedPipe(InputStream input, OutputStream output) throws IOException {
	    byte[] buffer = new byte[1024];
	    int byteCount;
	    while ((byteCount = input.read(buffer)) != -1) {
	    	output.write((Integer.toHexString(byteCount) + "\r\n").getBytes());
	        output.write(buffer, 0, byteCount);
	    	output.write("\r\n".getBytes());
	    }
    	output.write("0\r\n\r\n".getBytes());
	}
	
	public HttpStatusCode getStatusCode(){
		return statusCode;
	}
	
	public String getVersion(){
		return version;
	}
	
	public Map<String, Set<String>> getHeader() {
		return header;
	}
	
	public Set<String> getHeader(String key){
		Set<String> headerValues = header.get(key);
		if (headerValues == null) return Collections.emptySet();
		return headerValues;
	}
	
}
