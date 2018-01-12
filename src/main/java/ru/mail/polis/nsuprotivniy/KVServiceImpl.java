package ru.mail.polis.nsuprotivniy;

import one.nio.http.*;
import one.nio.net.ConnectionString;
import one.nio.server.AcceptorConfig;
import one.nio.server.ServerConfig;
import one.nio.util.Utf8;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
        HttpServerConfig config = new HttpServerConfig();
        config.selectors = Runtime.getRuntime().availableProcessors();
        AcceptorConfig ac = new AcceptorConfig();
        ac.port = port;
        ac.address = "localhost";
        config.acceptors = new AcceptorConfig[] {ac};
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

    @Path("/v0/entity_local")
    public void handleLocal(Request request, HttpSession session) throws IOException {
        if (handleEmptyKey(request.getParameter("id="), session) == false) return;
        switch (request.getMethod())
        {
            case Request.METHOD_GET: handleGetLocal(request, session); break;
            case Request.METHOD_PUT: handlePUTLocal(request, session); break;
            case Request.METHOD_DELETE: handleDELETELocal(request, session); break;
        }
    }

    public void handleGetLocal(Request request, HttpSession session) throws IOException {
        Response response = null;
        try {
            byte[] data = dao.getData(request.getParameter("id="));
            if (data == DAO.DELETED)
                response = new Response(Response.GONE, new byte[0]);
            else
                response = Response.ok(data);

        } catch (IOException e) {
            response = new Response(Response.NOT_FOUND, new byte[0]);
        }
        session.sendResponse(response);
    }

    public void handlePUTLocal(Request request, HttpSession session) throws IOException {
        try {

            dao.upsertData(request.getParameter("id="), request.getBody());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Response response = new Response(Response.CREATED, new byte[0]);
            session.sendResponse(response);
        }
    }

    public void handleDELETELocal(Request request, HttpSession session) throws IOException {
        try {
            dao.deleteData(request.getParameter("id="));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Response response = new Response(Response.ACCEPTED, new byte[0]);
            session.sendResponse(response);
        }
    }

    @Path("/v0/entity")
    public void handle(Request request, HttpSession session) throws IOException {
        String key = request.getParameter("id=");
        if (handleEmptyKey(key, session) == false) return;
        String replicas = request.getParameter("replicas=");
        int ack = (topology.size() >> 1) + 1, from = topology.size();
        if (replicas != null) {
            ack = Integer.parseInt(replicas.split("/")[0]);
            from = Integer.parseInt(replicas.split("/")[1]);
        }
        if (handleAckFrom(ack, from, session) == false) return;
        switch (request.getMethod())
        {
            case Request.METHOD_GET: handleGet(session, key, ack, from); break;
            case Request.METHOD_PUT: handlePUT(request, session, key, ack, from); break;
            case Request.METHOD_DELETE: handleDELETE(session, key, ack, from); break;
        }
    }


    public void handleGet(HttpSession session, String key, int ack, int from) throws IOException {
        Response response = null;
        try {
            int received = 0;
            int notFound = 0;
            byte[] data = null;

            for (String server : getServers(topology, key, from)) {
                if (server.endsWith(port + "")) {
                    try {
                        data = dao.getData(key);
                        if (data == DAO.DELETED)  {
                            session.sendResponse(new Response(Response.NOT_FOUND, new byte[0]));
                            return;
                        }
                        received++;
                    } catch (IOException e) {}
                    continue;
                }

                Response resp = null;
                try {
                    resp = new HttpClient(new ConnectionString(server)).get("/v0/entity_local?id=" + key);
                } catch (Exception e) {
                    continue;
                }

                if (resp.getStatus() == 200) {
                    received++;
                    data = (data == null) ? resp.getBody() : data;
                } else if (resp.getStatus() == 404) {
                    notFound++;
                } else if (resp.getStatus() == 410) {
                    session.sendResponse(new Response(Response.NOT_FOUND, new byte[0]));
                    return;
                }
            }

            if (received >= ack) {
                session.sendResponse(new Response(Response.OK, data));
            } else if (received == 0) {
                session.sendResponse(new Response(Response.NOT_FOUND, new byte[0]));
            } else if (notFound + received < ack) {
                session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, new byte[0]));
            }

        } catch (IOException e) {
            response = new Response(Response.NOT_FOUND, new byte[0]);
            session.sendResponse(response);
        }
    }

    public void handlePUT(Request request, HttpSession session, String key, int ack, int from) throws IOException {
        try {
            int received = 0;

            for (String server : getServers(topology, key, from)) {
                if (server.contains(port + "")) {
                    dao.upsertData(key, request.getBody());
                    received++;
                    continue;
                }

                Response resp = null;
                try {
                    resp = new HttpClient(new ConnectionString(server)).put("/v0/entity_local?id=" + key, request.getBody());
                } catch (Exception e) {
                    continue;
                }

                if (resp.getStatus() == 201) {
                    received++;
                }
            }

            if (received >= ack) {
                session.sendResponse(new Response(Response.CREATED, "created".getBytes()));
            } else {
                session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, new byte[0]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleDELETE(HttpSession session,  String key, int ack, int from) throws IOException {
        try {
            int received = 0;

            for (String server : getServers(topology, key, from)) {
                if (server.endsWith(port + "")) {
                    try {
                        dao.deleteData(key);
                        received++;
                    } catch (IOException e){}
                    continue;
                }


                Response resp = null;
                try {
                    resp = new HttpClient(new ConnectionString(server)).delete("/v0/entity_local?id=" + key);
                } catch (Exception e) {
                    continue;
                }

                if (resp.getStatus() == 202) {
                    received++;
                }
            }


            if (received >= ack) {
                session.sendResponse(new Response(Response.ACCEPTED, new byte[0]));
            } else {
                session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, new byte[0]));
            }
        } catch (IOException e) {
            e.printStackTrace();
            session.sendResponse(new Response(Response.NOT_FOUND, new byte[0]));
        }
    }


    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = Response.ok(Utf8.toBytes("<html><body><pre>Default</pre></body></html>"));
        response.addHeader("Content-Type: text/html");
        session.sendResponse(response);
    }

    boolean handleEmptyKey(String key, HttpSession session) throws IOException {
        if (key == null || key.length() == 0) {
            Response response = new Response(Response.BAD_REQUEST, new byte[0]);
            session.sendResponse(response);
            return false;
        }
        else {
            return true;
        }
    }

    boolean handleAckFrom(int ack, int from, HttpSession session) throws IOException {
        if (ack == 0 || from == 0 || ack > from) {
            Response response = new Response(Response.BAD_REQUEST, new byte[0]);
            session.sendResponse(response);
            return false;
        }
        else {
            return true;
        }
    }

    public static List<String> getServers(@NotNull Set<String> topology,
                                          @NotNull String id,
                                          final int from) {
        List<String> servers = new ArrayList<>();
        int index = (Math.abs(id.hashCode()) % topology.size()) - 1;

        for (int i = 0; i < from; i++) {
            index = (index + 1) % topology.size();
            servers.add(topology.toArray(new String[topology.size()])[index]);
        }

        return servers;
    }

    @Override
    public void stop() {
        super.stop();
        dao.close();
    }

}
