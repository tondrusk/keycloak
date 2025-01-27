/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.organization.admin.resource;

import java.util.Set;
import java.util.stream.Stream;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;
import org.keycloak.utils.StringUtil;

@Provider
public class OrganizationResource {

    private final KeycloakSession session;
    private final OrganizationProvider provider;
    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;

    public OrganizationResource() {
        // needed for registering to the JAX-RS stack
        this(null, null, null);
    }

    public OrganizationResource(KeycloakSession session, AdminPermissionEvaluator auth, AdminEventBuilder adminEvent) {
        this.session = session;
        this.provider = session == null ? null : session.getProvider(OrganizationProvider.class);
        this.auth = auth;
        this.adminEvent = adminEvent;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(OrganizationRepresentation organization) {
        if (organization == null) {
            throw new BadRequestException();
        }

        OrganizationModel model = provider.create(organization.getName());

        return Response.created(session.getContext().getUri().getAbsolutePathBuilder().path(model.getId()).build()).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<OrganizationRepresentation> get() {
        return provider.getAllStream().map(this::toRepresentation);
    }

    @Path("{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public OrganizationRepresentation get(@PathParam("id") String id) {
        if (StringUtil.isBlank(id)) {
            throw new BadRequestException();
        }

        return toRepresentation(getOrganization(id));
    }

    @Path("{id}")
    @DELETE
    public Response delete(@PathParam("id") String id) {
        if (StringUtil.isBlank(id)) {
            throw new BadRequestException();
        }

        provider.remove(getOrganization(id));

        return Response.noContent().build();
    }

    @Path("{id}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id, OrganizationRepresentation organization) {
        OrganizationModel model = getOrganization(id);

        toModel(organization, model);

        return Response.noContent().build();
    }

    @Path("{id}/members")
    public OrganizationMemberResource members(@PathParam("id") String id) {
        OrganizationModel organization = getOrganization(id);
        return new OrganizationMemberResource(session, organization, auth, adminEvent);
    }

    @Path("{id}/identity-provider")
    public OrganizationIdentityProviderResource identityProvider(@PathParam("id") String id) {
        return new OrganizationIdentityProviderResource(session, getOrganization(id), auth, adminEvent);
    }
    
    private OrganizationModel getOrganization(String id) {
        if (id == null) {
            throw new BadRequestException();
        }

        OrganizationModel model = provider.getById(id);

        if (model == null) {
            throw new NotFoundException();
        }

        return model;
    }

    private OrganizationRepresentation toRepresentation(OrganizationModel model) {
        if (model == null) {
            return null;
        }

        OrganizationRepresentation rep = new OrganizationRepresentation();

        rep.setId(model.getId());
        rep.setName(model.getName());
        rep.setAttributes(model.getAttributes());

        return rep;
    }

    private OrganizationModel toModel(OrganizationRepresentation rep, OrganizationModel model) {
        if (rep == null) {
            return null;
        }

        model.setName(rep.getName());

        if (rep.getAttributes() != null) {
            Set<String> attrsToRemove = model.getAttributes().keySet();
            attrsToRemove.removeAll(rep.getAttributes().keySet());
            attrsToRemove.forEach(model::removeAttribute);

            rep.getAttributes().entrySet().forEach(entry -> model.setAttribute(entry.getKey(), entry.getValue()));
        }

        return model;
    }
}
