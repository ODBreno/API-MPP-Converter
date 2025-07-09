# API-MPP-Converter

API responsável por converter arquivos `.mpp` (Microsoft Project) em projetos no Odoo automaticamente.

## 📚 Visão Geral

Esta API Java roda como um serviço no servidor de testes/backup da empresa. Ela é utilizada em conjunto com o fluxo de automação do **n8n**, responsável por monitorar a pasta compartilhada `projects` via SMB.

Sempre que um novo arquivo `.mpp` é detectado nessa pasta, a API realiza a conversão do arquivo para o formato JSON e envia os dados para o Odoo, criando automaticamente os projetos.

Após o processamento, o arquivo `.mpp` é movido para a subpasta `processados`, evitando reprocessamento.

## 📁 Padrão de Nomenclatura

Os arquivos `.mpp` devem seguir o padrão de nome:  departamento_nome_do_projeto.mpp



**Exemplo:**


## 🔧 Requisitos

- Java 11+
- Docker (para uso da imagem containerizada)
- Acesso ao servidor Odoo
- Acesso à pasta de rede via SMB

## 🚀 Executando com Docker

A imagem da API está disponível publicamente no GitHub Container Registry:

ghcr.io/odbreno/api-mpp-converter

Para executar via Docker:

```bash
docker run -d \
  --name api-mpp-converter \
  -e ODOO_HOST=endereco_odoo \
  -e ODOO_PORT=porta_do_odoo \
  -e ODOO_DB=nome_do_banco \
  -e ODOO_USER=email_do_usuario \
  -e ODOO_PASSWORD=senha_do_usuario \
  ghcr.io/odbreno/api-mpp-converter
````

Substitua os valores acima conforme o ambiente de destino (produção ou homologação).

🛠 Variáveis de Ambiente
As seguintes variáveis devem ser definidas no ambiente ou arquivo .env:

Variável	              Descrição
ODOO_HOST  - 	IP ou hostname do servidor Odoo
ODOO_PORT  -  Porta de acesso da API do Odoo
ODOO_DB  -  	Nome do banco de dados do Odoo
ODOO_USER  -  E-mail do usuário Odoo com acesso
ODOO_PASSWORD  -  Senha do usuário Odoo

🗃 Estrutura da Pasta
A API espera a seguinte estrutura de pastas:

projects/
├── Geoprocessamento_RIMU.mpp
└── processados/

🤝 Integração com n8n
O n8n é responsável por agendar e acionar a execução periódica da API, garantindo que novos arquivos .mpp sejam processados automaticamente sem intervenção manual.

