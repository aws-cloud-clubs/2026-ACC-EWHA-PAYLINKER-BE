package com.paylinker.api.auth;

import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class LinkSessionAuthentication extends AbstractAuthenticationToken {

    private final LinkSession session;

    public LinkSessionAuthentication(LinkSession session) {
        super(Collections.emptyList());
        this.session = session;
    }

    @Override
    public Object getCredentials() {
        return session.token();
    }

    @Override
    public Object getPrincipal() {
        return session.campaignRecipientId();
    }

    public LinkSession getSession() {
        return session;
    }
}
