
 package hipstershop;

 import static org.junit.Assert.assertEquals;
 import static org.junit.Assert.assertTrue;
 import static org.mockito.Mockito.mock;
 import static org.mockito.Mockito.times;
 import static org.mockito.Mockito.verify;
 
 import hipstershop.Demo.Ad;
 import hipstershop.Demo.AdRequest;
 import hipstershop.Demo.AdResponse;
 import io.grpc.ManagedChannel;
 import io.grpc.Server;
 import io.grpc.inprocess.InProcessChannelBuilder;
 import io.grpc.inprocess.InProcessServerBuilder;
 import io.grpc.stub.StreamObserver;
 import io.grpc.testing.GrpcCleanupRule;
 import java.io.IOException;
 import java.util.ArrayList;
 import java.util.Arrays;
 import java.util.List;
 import java.util.concurrent.CountDownLatch;
 import java.util.concurrent.TimeUnit;
 import org.junit.Before;
 import org.junit.Rule;
 import org.junit.Test;
 import org.junit.runner.RunWith;
 import org.junit.runners.JUnit4;
 
 /** Unit tests for {@link AdService}. */
 @RunWith(JUnit4.class)
 public final class AdServiceTest {
 
   /** This rule manages automatic graceful shutdown for the registered servers and channels at the end of test. */
   @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
 
   private Server server;
 
   /**
    * Starts up a new grpc server with a unique port before each test.
    *
    * @throws IOException if starting the server throws an exception.
    */
   @Before
   public void setUp() throws Exception {
     String serverName = InProcessServerBuilder.generateName();
 
     // Add a service
     server = InProcessServerBuilder.forName(serverName).addService(new AdService.AdServiceImpl()).build().start();
     // Create a client channel and register for automatic graceful shutdown.
     grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
   }
 
   /** Tests that the response is correct for a known request. */
   @Test
   public void getAds() throws Exception {
     List<String> input = new ArrayList<>(Arrays.asList("clothing", "accessories"));
     List<Ad> expectedAds = new ArrayList<>();
     expectedAds.addAll(AdService.createAdsMap().get("clothing"));
     expectedAds.addAll(AdService.createAdsMap().get("accessories"));
 
     ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(server.getAuthority()).directExecutor().build());
     hipstershop.AdServiceGrpc.AdServiceBlockingStub stub = hipstershop.AdServiceGrpc.newBlockingStub(channel);
     AdRequest request = AdRequest.newBuilder().addAllContextKeys(input).build();
     AdResponse response = stub.getAds(request);
     List<Ad> receivedAds = response.getAdsList();
     assertEquals(receivedAds.size(), expectedAds.size());
     assertTrue(receivedAds.containsAll(expectedAds));
   }
 
   /** Tests that the server responds correctly to a stream of requests. */
   @Test
   public void getAds_stream() throws Exception {
     final int numRequests = 10;
     final CountDownLatch finishLatch = new CountDownLatch(numRequests);
     List<String> input = new ArrayList<>(Arrays.asList("clothing", "accessories"));
     List<Ad> expectedAds = new ArrayList<>();
     expectedAds.addAll(AdService.createAdsMap().get("clothing"));
     expectedAds.addAll(AdService.createAdsMap().get("accessories"));
 
     ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(server.getAuthority()).directExecutor().build());
     hipstershop.AdServiceGrpc.AdServiceStub asyncStub = hipstershop.AdServiceGrpc.newStub(channel);
     StreamObserver<AdRequest> requestObserver =
         asyncStub.getAds(
             new StreamObserver<AdResponse>() {
               @Override
               public void onNext(AdResponse response) {
                 List<Ad> receivedAds = response.getAdsList();
                 assertEquals(receivedAds.size(), expectedAds.size());
                 assertTrue(receivedAds.containsAll(expectedAds));
                 finishLatch.countDown();
               }
 
               @Override
               public void onError(Throwable t) {
                 throw new RuntimeException(t);
               }
 
               @Override
               public void onCompleted() {}
             });
     AdRequest request = AdRequest.newBuilder().addAllContextKeys(input).build();
     for (int i = 0; i < numRequests; i++) {
       requestObserver.onNext(request);
     }
     requestObserver.onCompleted();
 
     assertTrue(finishLatch.await(1, TimeUnit.SECONDS));
   }
 
   /** Tests that the service will respond properly to an empty request. */
   @Test
   public void getAds_emptyRequest() throws Exception {
     ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(server.getAuthority()).directExecutor().build());
     hipstershop.AdServiceGrpc.AdServiceBlockingStub stub = hipstershop.AdServiceGrpc.newBlockingStub(channel);
     AdRequest request = AdRequest.newBuilder().build();
     AdResponse response = stub.getAds(request);
     assertTrue(response.getAdsList().size() <= AdService.MAX_ADS_TO_SERVE);
   }
 
   /** Tests that the server will close the connection on an empty stream. */
   @Test
   public void getAds_emptyStream() throws Exception {
     ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(server.getAuthority()).directExecutor().build());
     hipstershop.AdServiceGrpc.AdServiceStub asyncStub = hipstershop.AdServiceGrpc.newStub(channel);
     StreamObserver<AdRequest> requestObserver =
         asyncStub.getAds(
             new StreamObserver<AdResponse>() {
               @Override
               public void onNext(AdResponse response) {
                 assertTrue(response.getAdsList().size() <= AdService.MAX_ADS_TO_SERVE);
               }
 
               @Override
               public void onError(Throwable t) {
                 throw new RuntimeException(t);
               }
 
               @Override
               public void onCompleted() {}
             });
     requestObserver.onCompleted();
   }
 
   @Test
   public void getQuote_nullRequest() throws Exception {
     ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(server.getAuthority()).directExecutor().build());
     hipstershop.AdServiceGrpc.AdServiceBlockingStub stub = hipstershop.AdServiceGrpc.newBlockingStub(channel);
     // Setting a gRPC deadline to prevent the call from hanging.
     AdRequest request = null;
     StreamObserver<AdResponse> responseObserver = mock(StreamObserver.class);
     AdService.AdServiceImpl service = new AdService.AdServiceImpl();
     try {
       service.getAds(request, responseObserver);
     } catch (NullPointerException e) {
       verify(responseObserver, times(1)).onError(e);
     }
   }
 }
 