package com.cloudshare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class ClamAvService {

    @Value("${clamav.host:localhost}")
    private String host;

    @Value("${clamav.port:3310}")
    private int port;

    @Value("${clamav.timeout-ms:10000}")
    private int timeout;

    public boolean scan(InputStream inputStream) throws IOException {
        log.debug("Connecting to ClamAV daemon at {}:{}", host, port);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);

            try (OutputStream out = new BufferedOutputStream(socket.getOutputStream());
                 InputStream in = new BufferedInputStream(socket.getInputStream())) {

                // Send INSTREAM command (zINSTREAM\0 or nINSTREAM\n)
                // Using zINSTREAM\0 allows us to use null-byte delimiters
                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                out.flush();

                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    if (read > 0) {
                        // Header: 4-byte chunk size (big-endian)
                        byte[] lengthHeader = ByteBuffer.allocate(4).putInt(read).array();
                        out.write(lengthHeader);
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                }

                // Terminate stream with a zero-length chunk
                out.write(new byte[]{0, 0, 0, 0});
                out.flush();

                // Read ClamAV response
                ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
                byte[] respReadBuffer = new byte[1024];
                int respRead;
                while ((respRead = in.read(respReadBuffer)) != -1) {
                    responseBuffer.write(respReadBuffer, 0, respRead);
                }

                String response = responseBuffer.toString(StandardCharsets.US_ASCII).trim();
                log.info("ClamAV response: {}", response);

                if (response.contains("FOUND")) {
                    log.warn("ClamAV detected malware: {}", response);
                    return false;
                }
                return true;
            }
        } catch (IOException e) {
            log.error("ClamAV scanning failed due to connection/IO error", e);
            throw new IOException("Failed to communicate with ClamAV service", e);
        }
    }
}
