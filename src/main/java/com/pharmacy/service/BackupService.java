package com.pharmacy.service;

import com.pharmacy.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Creates a SQL dump of the database using mysqldump (ships with XAMPP). */
@Service
public class BackupService {
    private final AppProperties props;
    private final String url;
    private final String username;
    private final String password;

    public BackupService(AppProperties props,
                         @Value("${spring.datasource.url}") String url,
                         @Value("${spring.datasource.username}") String username,
                         @Value("${spring.datasource.password:}") String password) {
        this.props = props;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public byte[] dump() {
        // jdbc:mariadb://host:port/db?params
        URI uri = URI.create(url.substring("jdbc:".length()));
        String db = uri.getPath().substring(1);
        List<String> cmd = new ArrayList<>(List.of(props.backup().mysqldumpPath(),
                "--host=" + uri.getHost(), "--port=" + (uri.getPort() > 0 ? uri.getPort() : 3306),
                "--user=" + username, "--default-character-set=utf8mb4", "--single-transaction", "--routines", db));
        if (password != null && !password.isEmpty()) cmd.add(3, "--password=" + password);
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(out);
            }
            if (!p.waitFor(5, TimeUnit.MINUTES) || p.exitValue() != 0) {
                String err = new String(p.getErrorStream().readAllBytes());
                throw new BusinessException("error.backup", err);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("error.backup", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("error.backup", e.getMessage());
        }
    }
}
