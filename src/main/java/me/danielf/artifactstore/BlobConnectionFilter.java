package me.danielf.artifactstore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;

@Component
public class BlobConnectionFilter extends OncePerRequestFilter {

    private final DataSource dataSource;

    public BlobConnectionFilter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        //System.out.println(conn.);
        TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(conn));
        try {
            chain.doFilter(request, response); // controller runs, response streamed, all within this scope
        } finally {
            TransactionSynchronizationManager.unbindResource(dataSource);
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }
}