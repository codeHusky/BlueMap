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
package de.bluecolored.bluemap.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.bluecolored.bluemap.api.ChunkNotGeneratedException;

public class RenderManager {
	private static AtomicInteger nextRenderManagerInstanceNumber = new AtomicInteger(0);
	
	private Thread[] renderThreads;
	private BlockingQueue<RenderTicket> tickets;
	
	private Thread delayScheduleThread;
	private Map<RenderTicket, DelayedTicket> delayedTickets;
	
	private boolean paused;
	private boolean shutdown;
	
	private final int instanceNumber;
	
	public RenderManager(int threadCount) {
		this.instanceNumber = nextRenderManagerInstanceNumber.getAndIncrement();
		
		this.renderThreads = new Thread[threadCount];
		this.tickets = new LinkedBlockingQueue<>();
		
		this.delayScheduleThread = new Thread(this::delayScheduleThread);
		this.delayScheduleThread.setDaemon(true);
		this.delayScheduleThread.setName("BlueMap-RenderManager-" + instanceNumber + "-delaySchedule");
		this.delayedTickets = new ConcurrentHashMap<>();
		
		this.paused = false;
		this.shutdown = false;

		for (int i = 0; i < renderThreads.length; i++) {
			renderThreads[i] = new Thread(this::renderThread);
			renderThreads[i].setName("BlueMap-RenderManager-" + instanceNumber + "-" + i);
			renderThreads[i].setDaemon(true);
		}
		
	}
	
	/**
	 * Schedule a new tile to be rendered with a given tile-renderer
	 * 
	 * @return the scheduled render-ticket
	 */
	public RenderTicket scheduleRender(WorldTile tile, TileRenderer renderer) {
		RenderTicket ticket = new RenderTicket(tile, renderer);
		
		DelayedTicket delayedTicket;
		synchronized (delayedTickets) {
			delayedTicket = delayedTickets.remove(ticket);
		}
		
		if (delayedTicket != null) ticket.listener.addAll(delayedTicket.renderTicket.listener);
		
		tickets.add(ticket);
		return ticket;
	}
	
	/**
	 * Schedule a new tile to be rendered with a given tile-renderer, but delays the renderTicket so that:<br>
	 * if another ticket <b>with the same tile and renderer</b> is scheduled before the delay is expired it is merged with this ticket and only rendered once.
	 * 
	 * @return the scheduled render-ticket
	 */
	public RenderTicket scheduleDelayedRender(WorldTile tile, TileRenderer renderer, long delay) {
		if (delay <= 0)
			return scheduleRender(tile, renderer);
		
		RenderTicket ticket = new RenderTicket(tile, renderer);

		DelayedTicket delayedTicket;
		synchronized (delayedTickets) {
			delayedTicket = delayedTickets.putIfAbsent(ticket, new DelayedTicket(ticket, System.currentTimeMillis() + delay));
		}
		
		if (delayedTicket != null) return delayedTicket.renderTicket;
		
		delayScheduleThread.interrupt(); //interrupt to update the waiting time of this tread until the next scheduled render, only if a new ticket has been added to the map
		return ticket;
	}
	
	/**
	 * Starts the render threads.<br>
	 * This method can only be invoked once
	 */
	public void start() {
		for(Thread thread : renderThreads) {
			thread.start();
		}
		delayScheduleThread.start();
	}
	
	/**
	 * Pauses the threads from processing more tickets
	 */
	public void pause() {
		this.paused = true;
	}

	/**
	 * Resumes processing tickets after being paused 
	 */
	public void resume() {
		this.paused = false;
	}
	
	/**
	 * Stops the render threads.<br>
	 * This method can only be invoked once, after stopping an instance in can't be started again.
	 */
	public void shutdown() {
		if (!shutdown) {
			shutdown = true;
			delayScheduleThread.interrupt();
			for (Thread thread : renderThreads) {
				thread.interrupt();
			}
		}
	}
	
	/**
	 * Blocks until all threads have been stopped, the timeout has been reached, or the thread gets interrupted.<br>
	 * If the timeout is 0, this method blocks without timing out.
	 */
	public void awaitShutdown(long timeout, TimeUnit unit) throws InterruptedException {
		long time = System.currentTimeMillis() + unit.toMillis(timeout);
		for (Thread thread : renderThreads) {
			if (timeout > 0) thread.join(Math.max(1, time - System.currentTimeMillis()));
			else thread.join();
		}
	}
	
	/**
	 * Removes and returns all scheduled tickets
	 */
	public Collection<RenderTicket> drainScheduledTickets(){
		Collection<RenderTicket> drainedTickets = new ArrayList<>(tickets.size());
		tickets.drainTo(drainedTickets);
		synchronized (delayedTickets) {
			for (DelayedTicket ticket : delayedTickets.values()) {
				drainedTickets.add(ticket.renderTicket);
			}
			delayedTickets.clear();
		}
		return drainedTickets;
	}
	
	/**
	 * Returns the current number of scheduled tickets
	 */
	public int getScheduledTicketCount() {
		return tickets.size() + delayedTickets.size();
	}
	
	/**
	 * Returns true if this render-manager is paused
	 */
	public boolean isPaused() {
		return paused;
	} 
	
	private void renderThread() {
		while (!shutdown) {
			try {
				while (paused) Thread.sleep(250);
				tickets.take().process();
			} catch (InterruptedException e) {}
		}
	}

	private void delayScheduleThread() {
		while (!shutdown) {
			try {
				long minTime = 10000;
				
				synchronized(delayedTickets) {
					long now = System.currentTimeMillis();
					Iterator<DelayedTicket> tileIterator = delayedTickets.values().iterator();
					while (tileIterator.hasNext()) {
						DelayedTicket delayedTicket = tileIterator.next();
						if (delayedTicket.scheduleTime <= now) {
							tileIterator.remove();
							tickets.add(delayedTicket.renderTicket);
						} else {
							long waitTime = delayedTicket.scheduleTime - now;
							if (waitTime < minTime) minTime = waitTime;
						} 
					}
				}
				
				//System.out.println(tickets.size() + " + " + delayedTickets.size() + " = " + getScheduledTicketCount());
				
				Thread.sleep(Math.max(minTime, 0));
			} catch (InterruptedException e) {}
		}
	}
	
	public class RenderTicket {
		
		private final TileRenderer renderer;
		private final WorldTile tile;
		private Exception exception;
		private boolean done;
		
		private Collection<Consumer<RenderTicket>> listener;
		
		private int hash;
		
		private RenderTicket(WorldTile tile, TileRenderer renderer) {
			this.tile = tile;
			this.renderer = renderer;
			
			this.done = false;
			this.exception = null;
			
			this.listener = new ArrayList<>();
			
			this.hash = Objects.hash(this.renderer, this.tile);
		}
		
		private void process() {
			synchronized (this) {
				if (this.done) throw new IllegalStateException("Ticket is already done!");
				
				try {
					this.renderer.render(this.tile);
				} catch (Exception ex) {
					this.exception = ex;
				}
				
				this.done = true;	
			}
			
			for (Consumer<RenderTicket> listener : this.listener) {
				listener.accept(this);
			}
			
			this.listener.clear();
		}
		
		/**
		 * Checks if this ticket has thrown any errors while being processed
		 */
		public void check() throws IOException, ChunkNotGeneratedException {
			if (!done) throw new IllegalStateException("Ticket is not yet done!");
			
			if (exception == null) return;
			if (exception instanceof IOException) throw (IOException) exception;
			if (exception instanceof ChunkNotGeneratedException) throw (ChunkNotGeneratedException) exception;
			if (exception instanceof RuntimeException) throw (RuntimeException) exception;
			
			throw new RuntimeException("Unexpected Exception", exception);
		}
		
		/**
		 * Checks if this ticket has been processed yet
		 */
		public boolean isDone() {
			return done;
		}
		
		/**
		 * <p>Adds a listener that gets called when the ticket is done.<br>
		 * The order the listeners are called is not defined.</p>
		 * <p>If the ticket is already done, the listener is called immediately</p>
		 */
		public void addListener(Consumer<RenderTicket> listener) {
			if (isDone()) listener.accept(this);
			else this.listener.add(listener);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof RenderTicket)) return false;
			RenderTicket that = (RenderTicket) obj;
			
			if (!this.renderer.equals(that.renderer)) return false;
			return this.tile.equals(that.tile);
		}
		
		@Override
		public int hashCode() {
			return hash;
		}
		
		public WorldTile getTile() {
			return tile;
		}
		
		public TileRenderer getTileRenderer() {
			return renderer;
		}
		
	}
	
	private class DelayedTicket {
		public final RenderTicket renderTicket;
		public final long scheduleTime;

		public DelayedTicket(RenderTicket renderTicket, long scheduleTime) {
			this.renderTicket = renderTicket;
			this.scheduleTime = scheduleTime;
		}
	}
	
}
