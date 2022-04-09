/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ddmlib.internal

import com.android.ddmlib.Log
import com.android.ddmlib.internal.commands.CommandResult
import com.android.ddmlib.internal.commands.ICommand
import java.io.EOFException
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Timer
import java.util.TimerTask

/**
 * Class to accept external connections and issue commands on a running ddmlib service.
 * The format of the incoming data is expected to match the format of adb commands
 * String formatted as follows: [Size(in hex 4 chars)][command]:[args]
 * Example: 001Cdisconnect:device-id:1234
 */
internal class CommandService(private val mListenPort: Int) : Runnable {

    private var listenChannel: ServerSocketChannel? = null
    private var serverAddress: InetSocketAddress? = null
    private var quit = true
    private var runThread: Thread? = null
    private var startTimer: Timer? = null
    private val commandMap = HashMap<String, ICommand>()
    val boundPort: Int
        get() = listenChannel?.socket()?.localPort ?: -1

    fun addCommand(command: String, handler: ICommand) {
        commandMap[command] = handler
    }

    fun stop() {
        quit = true

        if (listenChannel != null) {
            try {
                listenChannel!!.close()
                listenChannel!!.socket().close()
            } catch (ex: IOException) {
                // Failed to close server socket.
                Log.w("CommandService", ex)
            }
        }
        // Wait until our run thread exits. This guarantees we can clean up all data used by
        // this thread without risk of threading issues.
        if (runThread != null) {
            try {
                runThread!!.join(JOIN_TIMEOUT_MS)
                if (runThread!!.isAlive) {
                    Log.e(
                        "CommandService",
                        "Run thread still alive after " + JOIN_TIMEOUT_MS + "ms"
                    )
                }
            } catch (ex: InterruptedException) {
                // Failed to wait for thread to stop.
                Log.w("CommandService", ex)
            }
        }

        listenChannel = null
        runThread = null
    }

    fun start() {
        if (startTimer == null) {
            startTimer = Timer("CommandServiceTimer")
            startTimer!!.schedule(ServerHostTimer(), 0, RETRY_SERVER_MILLIS)
        }
    }

    override fun run() {
        while (!quit) {
            try {
                val client = listenChannel?.accept() ?: continue
                client.use { processOneCommand(it) }
            } catch (ex: IOException) {
                // threw an exception why calling select, log error and exit
                Log.e("CommandService", ex)
                return
            }
        }
    }

    fun processOneCommand(client: SocketChannel) {
        var buffer = readExactly(client, 4)
        val cmdSize = Integer.parseInt(UTF_8.decode(buffer).toString(),16)
        buffer = readExactly(client, cmdSize)
        val data = UTF_8.decode(buffer).toString()
        // Check if this command has any arguments, if not run command if
        // one matches.
        val commandTerminator = data.indexOf(":")
        if (commandTerminator == -1 && commandMap.containsKey(data)) {
            write(commandMap[data]!!.run(null),client)
        } else if (commandTerminator != -1) {
            val command = data.substring(0, commandTerminator)
            val argsString = data.substring(commandTerminator + 1)
            if (!commandMap.containsKey(command)) {
                Log.w("CommandService", "Unknown command received")
                return
            }
            try {
                write(commandMap[command]!!.run(argsString), client)
            } catch (t: Throwable) {
                Log.w("CommandService", t)
            }
        } else {
            Log.w("CommandService", "Failed to find command")
        }
    }

    fun readExactly(client: SocketChannel, amount: Int) : ByteBuffer {
        val buffer = ByteBuffer.allocate(amount)
        while (buffer.hasRemaining()) {
            val count = client.read(buffer)
            if (count == -1) {
                throw EOFException("Unexpected end of channel")
            }
        }
        buffer.position(0)
        return buffer
    }

    fun write(result: CommandResult, client: SocketChannel) {
        if (result.success) {
            client.write(wrapString("OKAY"))
        } else {
            client.write(wrapString(String.format("FAIL%04x%s",
                                                  result.message.length,
                                                  result.message)))
        }
    }

   private fun wrapString(str: String): ByteBuffer = ByteBuffer.wrap(str.toByteArray(UTF_8))

    internal inner class ServerHostTimer : TimerTask() {
        override fun run() {
            try {
                serverAddress = InetSocketAddress(
                    InetAddress.getByName("localhost"),  // $NON-NLS-1$
                    mListenPort
                )
                listenChannel = ServerSocketChannel.open()
                listenChannel!!.socket().reuseAddress = true // enable SO_REUSEADDR
                listenChannel!!.socket().bind(serverAddress)
            } catch (ex: BindException) {
                // A server is already running, setup timer to retry in X seconds.
                Log.i("CommandService", "Port is already bound")
                return
            } catch (ex: IOException) {
                // Failed to open server for unknown reason
                Log.e("CommandService", ex)
                return
            }
            quit = false
            runThread = Thread(this@CommandService, "CommandServiceConnection")
            runThread!!.start()
            startTimer!!.cancel()
            startTimer = null
        }
    }

    companion object {
        private const val JOIN_TIMEOUT_MS: Long = 5000 // 5 seconds
        private const val RETRY_SERVER_MILLIS = (30 * 1000).toLong()  // 30 seconds
    }
}
