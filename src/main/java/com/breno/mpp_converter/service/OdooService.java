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
import java.util.HashMap;
import java.util.List;
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

    public void connect() throws MalformedURLException, XmlRpcException {
        XmlRpcClient authClient = new XmlRpcClient();
        XmlRpcClientConfigImpl authConfig = new XmlRpcClientConfigImpl();
        authConfig.setServerURL(new URL(String.format("%s/xmlrpc/2/common", url)));
        authClient.setConfig(authConfig);

        Object result = authClient.execute("authenticate", Arrays.asList(db, username, apiKey, Collections.emptyMap()));
        if (result instanceof Integer) {
            this.uid = (Integer) result;
        } else {
            throw new RuntimeException("Falha na autenticação com o Odoo.");
        }

        this.objectClient = new XmlRpcClient();
        XmlRpcClientConfigImpl objectConfig = new XmlRpcClientConfigImpl();
        objectConfig.setServerURL(new URL(String.format("%s/xmlrpc/2/object", url)));
        this.objectClient.setConfig(objectConfig);
    }

    public int create(String model, Map<String, Object> values) throws XmlRpcException {
        Object newId = this.objectClient.execute("execute_kw", Arrays.asList(
                db, this.uid, this.apiKey, model, "create", Collections.singletonList(values)));
        return (Integer) newId;
    }

    public void update(String model, int id, Map<String, Object> values) throws XmlRpcException {
        this.objectClient.execute("execute_kw", Arrays.asList(
                db, this.uid, this.apiKey, model, "write", Arrays.asList(Collections.singletonList(id), values)));
    }

    /**
     * Pesquisa por registros em um modelo do Odoo com base em um domínio (filtro).
     * 
     * @param model  O nome técnico do modelo (ex: "project.tags").
     * @param domain A condição de busca (ex: [["name", "=", "ENGENHARIA"]]).
     * @return Um array de IDs dos registros encontrados.
     */
    public Object[] search(String model, List<Object> domain) throws XmlRpcException {
        Object result = this.objectClient.execute("execute_kw", Arrays.asList(
                db,
                this.uid,
                this.apiKey,
                model,
                "search",
                Collections.singletonList(domain)));
        return (Object[]) result;
    }

    /**
     * Procura por uma tag pelo nome. Se não existir, cria uma nova.
     * 
     * @param tagName O nome da tag a ser procurada/criada.
     * @return O ID da tag no Odoo.
     */
    public int findOrCreateTag(String tagName) throws XmlRpcException {
        // 1. Procura pela tag (o domínio é uma lista de listas)
        List<Object> domain = Collections.singletonList(Arrays.asList("name", "=", tagName));
        Object[] existingTagIds = search("project.tags", domain);

        if (existingTagIds.length > 0) {
            // 2. Se a tag já existe, retorna o ID dela
            System.out.println("Tag '" + tagName + "' já existe com ID: " + existingTagIds[0]);
            return (Integer) existingTagIds[0];
        } else {
            // 3. Se não existe, cria uma nova
            System.out.println("Tag '" + tagName + "' não encontrada. Criando nova tag...");
            Map<String, Object> tagValues = new HashMap<>();
            tagValues.put("name", tagName);
            int newTagId = create("project.tags", tagValues);
            System.out.println("Tag '" + tagName + "' criada com ID: " + newTagId);
            return newTagId;
        }
    }
}
