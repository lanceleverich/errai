package org.jboss.workspace.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import org.jboss.workspace.client.framework.AcceptsCallback;
import org.jboss.workspace.client.rpc.MessageBusService;
import org.jboss.workspace.client.rpc.CommandMessage;
import org.jboss.workspace.client.rpc.protocols.SecurityCommands;
import org.jboss.workspace.client.rpc.protocols.SecurityParts;
import org.jboss.workspace.server.bus.Message;
import org.jboss.workspace.server.bus.MessageBus;
import org.jboss.workspace.server.bus.SimpleMessageBusProvider;
import org.jboss.workspace.server.bus.MessageBusServer;
import static org.jboss.workspace.server.bus.MessageBusServer.decodeToCommandMessage;
import org.jboss.workspace.server.json.JSONUtil;
import org.jboss.workspace.server.security.auth.AuthorizationAdapter;
import org.jboss.workspace.server.security.auth.JAASAdapter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import static java.lang.Thread.currentThread;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class MessageBusServiceImpl extends RemoteServiceServlet implements MessageBusService {
    private MessageBus bus;
    private AuthorizationAdapter authorizationAdapter;

    public static final String AUTHORIZATION_SVC_SUBJECT = "AuthorizationService";

    @Override
    public void init() throws ServletException {
        // just use the simple bus for now.  more integration options to come...
        bus = new SimpleMessageBusProvider().getBus();

        loadConfig();

        //todo: this all needs to be refactored at some point.
        bus.subscribe(AUTHORIZATION_SVC_SUBJECT, new AcceptsCallback() {
            public void callback(Object message, Object data) {
                CommandMessage c = decodeToCommandMessage(message);

                switch (SecurityCommands.valueOf(c.getCommandType())) {
                    case WhatCredentials:
                         //todo: we only support login/password for now
                        CommandMessage reply = new CommandMessage(SecurityCommands.WhatCredentials)
                                .set(SecurityParts.CredentialsRequired, "Name,Password")
                                .set(SecurityParts.ReplyTo, AUTHORIZATION_SVC_SUBJECT);

                         MessageBusServer.store(c.get(String.class, SecurityParts.ReplyTo), reply);
                }

            }
        });

        bus.subscribe("ServerEchoService", new AcceptsCallback() {
            public void callback(Object message, Object data) {

                if (message == null) return;
                Map map = JSONUtil.decodeToMap(String.valueOf(message));

                if (map.containsKey("EchoBackData")) {
                    System.out.println("EchoBack: " + map.get("EchoBackData"));
                }
            }
        });



        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000 * 5);

                    Map<String, Object> msg = new HashMap<String, Object>();
                    msg.put("CommandType", "Hello");
                    msg.put("Name", "Jay Balunas");

                    bus.store("org.jboss.workspace.WorkspaceLayout", msg);

                }
                catch (InterruptedException e) {
                }

            }
        };

        t.start();
    }

    private void loadConfig() {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("workspace");
            String authenticationAdapterClass = bundle.getString("workspace.authentication_adapter");

            try {
                Class clazz = Class.forName(authenticationAdapterClass, false, currentThread().getContextClassLoader());
                authorizationAdapter = (AuthorizationAdapter) clazz.newInstance();
            }
            catch (Exception e) {
                throw new RuntimeException("could not instantiate authentication adapter:" + authenticationAdapterClass, e);
            }
        }
        catch (MissingResourceException e) {
            useDefaults();
        }
    }

    private void useDefaults() {
        authorizationAdapter = new JAASAdapter();
    }

    public void store(String subject, String message) {
        bus.store(subject, message);
    }

    public String[] nextMessage() {
        Message m = bus.nextMessage(getId());
        if (m != null) {
            return new String[]{m.getSubject(), String.valueOf(m.getMessage())};
        }
        else {
            return null;
        }
    }

    public void remoteSubscribe(String subject) {
        if (bus.getSubjects().contains(subject)) return;
        bus.remoteSubscribe(getId(), subject);
    }

    private String getId() {
        HttpServletRequest request = getThreadLocalRequest();
        HttpSession session = request.getSession();

        if (session.getAttribute("WSSessionID") == null) {
            session.setAttribute("WSSessionID", session.getId());
        }

        return (String) session.getAttribute("WSSessionID");
    }

    public String[] getSubjects() {
        return bus.getSubjects().toArray(new String[bus.getSubjects().size()]);
    }
}
