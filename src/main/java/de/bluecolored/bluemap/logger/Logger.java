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
package de.bluecolored.bluemap.logger;

public interface Logger {

	void logError(String message, Throwable throwable);

	void logWarning(String message);
	
	void logInfo(String message);

	void logDebug(String message);
	
	/**
	 * Only log the error if no message has been logged before with the same key.
	 */
	void noFloodError(String key, String message, Throwable throwable);

	/**
	 * Only log the warning if no message has been logged before with the same key.
	 */
	void noFloodWarning(String key, String message);

	/**
	 * Only log the info if no message has been logged before with the same key.
	 */
	void noFloodInfo(String key, String message);

	/**
	 * Only log the debug-message if no message has been logged before with the same key.
	 */
	void noFloodDebug(String key, String message);

	/**
	 * Only log the error if no message has been logged before with the same content.
	 */
	default void noFloodError(String message, Throwable throwable){
		noFloodError(message, message, throwable);
	}

	/**
	 * Only log the warning if no message has been logged before with the same content.
	 */
	default void noFloodWarning(String message){
		noFloodWarning(message, message);
	}

	/**
	 * Only log the info if no message has been logged before with the same content.
	 */
	default void noFloodInfo(String message){
		noFloodInfo(message, message);
	}

	/**
	 * Only log the debug-message if no message has been logged before with the same content.
	 */
	default void noFloodDebug(String message){
		noFloodDebug(message, message);
	}
	
	void clearNoFloodLog();
	
	void removeNoFloodKey(String key);
	
	default void removeNoFloodMessage(String message){
		removeNoFloodKey(message);
	}
	
	public static Logger stdOut(){
		return new PrintStreamLogger(System.out, System.err);
	}
	
}
