package com.breno.mpp_converter.service;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@Service
public class OdooService {

    @Value("${odoo.url}")
    private String url;
    @Value("${odoo.db}")
    private String db;
    @Value("${odoo.username}")
    private String username;
    @Value("${odoo.api-key}")
    private String apiKey;

    private int uid;
    private XmlRpcClient objectClient;

    /**
     * Autentica no Odoo e configura os clientes RPC.
     * 
     * @throws MalformedURLException Se a URL do Odoo for inválida.
     * @throws XmlRpcException       Se a autenticação falhar.
     */
    public void connect() throws MalformedURLException, XmlRpcException {
        // Cliente para autenticação
        XmlRpcClient authClient = new XmlRpcClient();
        XmlRpcClientConfigImpl authConfig = new XmlRpcClientConfigImpl();
        authConfig.setServerURL(new URL(String.format("%s/xmlrpc/2/common", url)));
        authClient.setConfig(authConfig);

        // O login retorna o ID do usuário (uid)
        Object result = authClient.execute("authenticate", Arrays.asList(db, username, apiKey, Collections.emptyMap()));
        if (result instanceof Integer) {
            this.uid = (Integer) result;
        } else {
            throw new RuntimeException("Falha na autenticação com o Odoo.");
        }

        // Cliente para executar operações nos modelos (ex: criar tarefa)
        this.objectClient = new XmlRpcClient();
        XmlRpcClientConfigImpl objectConfig = new XmlRpcClientConfigImpl();
        objectConfig.setServerURL(new URL(String.format("%s/xmlrpc/2/object", url)));
        this.objectClient.setConfig(objectConfig);
    }

    /**
     * Método genérico para criar um novo registro em qualquer modelo do Odoo.
     * 
     * @param model  O nome técnico do modelo (ex: "project.project",
     *               "project.task").
     * @param values Um mapa com os campos e valores a serem criados.
     * @return O ID do registro recém-criado no Odoo.
     * @throws XmlRpcException Se a criação falhar.
     */
    public int create(String model, Map<String, Object> values) throws XmlRpcException {
        Object newId = this.objectClient.execute("execute_kw", Arrays.asList(
                db,
                this.uid,
                this.apiKey,
                model,
                "create",
                Collections.singletonList(values)));
        return (Integer) newId;
    }

    /**
     * Método genérico para atualizar um registro existente no Odoo.
     * 
     * @param model  O nome técnico do modelo.
     * @param id     O ID do registro a ser atualizado.
     * @param values Um mapa com os campos e valores a serem atualizados.
     * @throws XmlRpcException Se a atualização falhar.
     */
    public void update(String model, int id, Map<String, Object> values) throws XmlRpcException {
        this.objectClient.execute("execute_kw", Arrays.asList(
                db,
                this.uid,
                this.apiKey,
                model,
                "write",
                Arrays.asList(Collections.singletonList(id), values)));
    }
}
