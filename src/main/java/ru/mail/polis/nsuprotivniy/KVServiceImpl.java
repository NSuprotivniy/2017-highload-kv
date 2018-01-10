package ru.mail.polis.nsuprotivniy;

import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVService;

import java.io.IOException;
import java.util.Set;


public class KVServiceImpl extends HttpServer implements KVService {

    private DAO dao;
    private Set<String> topology;

    public KVServiceImpl(int port,
                         @NotNull final DAO dao,
                         @NotNull final Set<String> topology) throws IOException {
        super(createHttpServerConfig(port));
        this.dao = dao;
        this.topology = topology;
    }

    public static HttpServerConfig createHttpServerConfig(int port) {
        AcceptorConfig ac = new AcceptorConfig();
        ac.port = port;

        HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[]{ac};
        return config;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            super.handleRequest(request, session);
        } catch (RuntimeException e) {
            session.sendError(Response.BAD_REQUEST, e.toString());
        }
    }

    @Path("/v0/entity")
    public void handle(Request request, HttpSession session) throws IOException {
        switch (request.getMethod())
        {
            case Request.METHOD_GET: handleGet(request, session); break;
            case Request.METHOD_PUT: handlePUT(request, session); break;
            case Request.METHOD_DELETE: handleDELETE(request, session); break;
        }
    }

    public void handleGet(Request request, HttpSession session) throws IOException {
        Response response = null;
        try {
            byte[] data = dao.getData(request.getParameter("id"));
            response = Response.ok(data);
        } catch (IOException e) {
            response = new Response(Response.NOT_FOUND);

        }
        session.sendResponse(response);
    }

    public void handlePUT(Request request, HttpSession session) throws IOException {
        dao.upsertData(request.getParameter("id"), request.getBody());
        Response response = new Response(Response.CREATED);
        session.sendResponse(response);
    }

    public void handleDELETE(Request request, HttpSession session) throws IOException {
        dao.deleteData(request.getParameter("id"));
        Response response = new Response(Response.ACCEPTED);
        session.sendResponse(response);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = Response.ok(Utf8.toBytes("<html><body><pre>Default</pre></body></html>"));
        response.addHeader("Content-Type: text/html");
        session.sendResponse(response);
    }
}
