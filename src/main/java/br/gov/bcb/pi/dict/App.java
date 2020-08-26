package br.gov.bcb.pi.dict;

import br.gov.bcb.pi.dict.api.ClaimApi;
import br.gov.bcb.pi.dict.api.DirectoryApi;
import br.gov.bcb.pi.dict.api.ReconciliationApi;
import br.gov.bcb.pi.dict.api.model.Claim;
import br.gov.bcb.pi.dict.api.model.ClaimType;
import br.gov.bcb.pi.dict.api.model.CreateClaimRequest;
import br.gov.bcb.pi.dict.api.model.CreateClaimResponse;
import br.gov.bcb.pi.dict.api.model.CreateEntryRequest;
import br.gov.bcb.pi.dict.api.model.CreateEntryResponse;
import br.gov.bcb.pi.dict.api.model.Entry;
import br.gov.bcb.pi.dict.api.model.GetEntryByCidResponse;
import br.gov.bcb.pi.dict.api.model.GetEntryResponse;
import br.gov.bcb.pi.dict.api.model.Problem;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Preconditions;

import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static br.gov.bcb.pi.dict.api.model.EntryOperationReason.USER_REQUESTED;
import static com.google.common.hash.Hashing.hmacSha256;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;


public class App {
    private static final String SECRET_SIGNING_KEY = "chave-secreta-participante";
    private static final App instance = new App();

    @Parameter(names = "-ispb", description = "ISPB do participante")
    private String ispb = "12345678";
    @Parameter(names = "-signatureKeyStore", description = "KeyStore com chave para assinatura digital")
    private String signatureKeyStore = "signature.jks";
    @Parameter(names = "-signatureKeyStorePassword", description = "Senha da KeyStore para assinatura digital")
    private String signatureKeyStorePassword = "signature.jks";
    @Parameter(names = "-baseAddress", description = "Endereço-base da API")
    private String baseAddress = "https://dict-h.pi.rsfn.net.br/api/v1";

    private DirectoryApi dictApi;
    private ClaimApi claimApi;
    private ReconciliationApi reconciliationApi;

    public static void main(String[] args) {
        JCommander.newBuilder()
                .addObject(instance)
                .build()
                .parse(args);
        instance.run();
    }

    private void run() {
        this.dictApi = ApiClientFactory.createApiClient(baseAddress, DirectoryApi.class);
        this.claimApi = ApiClientFactory.createApiClient(baseAddress, ClaimApi.class);
        this.reconciliationApi = ApiClientFactory.createApiClient(baseAddress, ReconciliationApi.class);

        // cria um vínculo no DICT
        Entry entry = ObjectFactory.createRandomEntry(ispb);
        UUID requestId = UUID.randomUUID();
        CreateEntryRequest createRequest = new CreateEntryRequest().entry(entry).reason(USER_REQUESTED).requestId(requestId);
        handleCreateEntryResponse(createRequest, this.dictApi.createEntry(createRequest));

        // consulta vínculo pelo cid
        String cid = Utils.cidForEntry(entry, requestId);
        handleGetEntryByCidResponse(this.reconciliationApi.getEntryByCid(cid, ispb));

        // consulta um vínculo
        String randomCpf = randomNumeric(11);
        String piPayerId = hmacSha256(SECRET_SIGNING_KEY.getBytes()).hashString(randomCpf, StandardCharsets.UTF_8).toString();
        String piEndToEndId = randomAlphanumeric(32);

        String key = "00038166000105";
        handleGetEntryResponse(this.dictApi.getEntry(key, ispb, piPayerId, piEndToEndId));
    }


    private void handleCreateEntryResponse(CreateEntryRequest createRequest, Response response) {
        Preconditions.checkNotNull(response);
        switch (response.getStatus()) {
            case 201:
                System.out.println(">> Retorno: " + response.readEntity(CreateEntryResponse.class).getEntry());
                break;
            case 400:
                Problem problem = response.readEntity(Problem.class);
                System.out.println(">> Retorno: " + response.readEntity(Problem.class));

                ClaimType claimTypeToCreate = null;
                if (problem.getType().endsWith("/EntryKeyOwnedByDifferentPerson")) {
                    claimTypeToCreate = ClaimType.OWNERSHIP;
                } else if (problem.getType().endsWith("/EntryKeyInCustodyOfDifferentParticipant")) {
                    claimTypeToCreate = ClaimType.PORTABILITY;
                }

                if (claimTypeToCreate != null) {
                    Entry entry = createRequest.getEntry();
                    Claim claim = new Claim()
                            .type(claimTypeToCreate)
                            .key(entry.getKey())
                            .keyType(entry.getKeyType())
                            .claimer(entry.getOwner())
                            .claimerAccount(entry.getAccount());
                    handleCreateClaimResponse(this.claimApi.createClaim(new CreateClaimRequest().claim(claim)));
                }

                break;
            case 403:
            case 503:
                System.out.println(">> Retorno: " + response.readEntity(String.class));
                break;
            default:
                System.out.println(">> Retorno: " + response.getStatus());
        }

    }

    private void handleGetEntryByCidResponse(Response response) {
        Preconditions.checkNotNull(response);
        switch (response.getStatus()) {
            case 200:
                System.out.println(">> Retorno: " + response.readEntity(GetEntryByCidResponse.class).getEntry());
                break;
            case 400:
            case 404:
                System.out.println(">> Retorno: " + response.readEntity(Problem.class));
                break;
            default:
                System.out.println(">> Retorno: " + response.getStatus());
        }
    }

    private void handleCreateClaimResponse(Response response) {
        Preconditions.checkNotNull(response);
        switch (response.getStatus()) {
            case 201:
                System.out.println(">> Retorno: " + response.readEntity(CreateClaimResponse.class).getClaim());
                break;
            case 400:
            case 403:
            case 503:
                System.out.println(">> Retorno: " + response.readEntity(Problem.class));
                break;
            default:
                System.out.println(">> Retorno: " + response.getStatus());
        }
    }

    private void handleGetEntryResponse(Response response) {
        Preconditions.checkNotNull(response);
        switch (response.getStatus()) {
            case 200:
                System.out.println(">> Retorno: " + response.readEntity(GetEntryResponse.class).getEntry());
                break;
            case 400:
            case 404:
                System.out.println(">> Retorno: " + response.readEntity(Problem.class));
                break;
            default:
                System.out.println(">> Retorno: " + response.getStatus());
        }

    }


    public static App getInstance() {
        return instance;
    }

    public String getSignatureKeyStore() {
        return this.signatureKeyStore;
    }

    public String getSignatureKeyStorePassword() {
        return this.signatureKeyStorePassword;
    }
}
