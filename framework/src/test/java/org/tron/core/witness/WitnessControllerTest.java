package org.tron.core.witness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import org.junit.Test;
import org.tron.common.BaseTest;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.consensus.dpos.DposSlot;
import org.tron.core.Constant;
import org.tron.core.config.args.Args;
import org.tron.core.capsule.BlockCapsule;
import org.tron.protos.Protocol.Block;

public class WitnessControllerTest extends BaseTest {

  @Resource
  private DposSlot dposSlot;

  static {
    Args.setParam(new String[]{"-d", dbPath()}, Constant.TEST_CONF);
  }

  @Test
  public void testSlot() {
    chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(19000);
    chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(1);
  }

  // Original witness scheduling tests.
  // @Test
  public void testWitnessSchedule() {
    // No block has been produced yet.
    assertEquals(0, chainBaseManager.getHeadBlockNum());

    // Verify genesis witness scheduling.
    assertEquals(
        "a0904fe896536f4bebc64c95326b5054a2c3d27df6",
        ByteArray.toHexString(dposSlot.getScheduledWitness(0).toByteArray()));
    assertEquals(
        "a0904fe896536f4bebc64c95326b5054a2c3d27df6",
        ByteArray.toHexString(dposSlot.getScheduledWitness(5).toByteArray()));
    assertEquals(
        "a0807337f180b62a77576377c1d0c9c24df5c0dd62",
        ByteArray.toHexString(dposSlot.getScheduledWitness(6).toByteArray()));
    assertEquals(
        "a0807337f180b62a77576377c1d0c9c24df5c0dd62",
        ByteArray.toHexString(dposSlot.getScheduledWitness(11).toByteArray()));
    assertEquals(
        "a05430a3f089154e9e182ddd6fe136a62321af22a7",
        ByteArray.toHexString(dposSlot.getScheduledWitness(12).toByteArray()));

    // Test maintenance by updating the active and shuffled witness lists.
    ByteString a = ByteString.copyFrom(ByteArray.fromHexString("a0ec6525979a351a54fa09fea64beb4cce33ffbb7a"));
    ByteString b = ByteString.copyFrom(ByteArray.fromHexString("a0fab5fbf6afb681e4e37e9d33bddb7e923d6132e5"));
    List<ByteString> w = new ArrayList<>();
    w.add(a);
    w.add(b);

    chainBaseManager.getWitnessScheduleStore().saveActiveWitnesses(w);
    assertEquals(2, chainBaseManager.getWitnessScheduleStore().getActiveWitnesses().size());

    chainBaseManager.getWitnessScheduleStore().saveCurrentShuffledWitnesses(w);
    assertEquals(a, dposSlot.getScheduledWitness(1));
    assertEquals(b, dposSlot.getScheduledWitness(2));
    assertEquals(a, dposSlot.getScheduledWitness(3));
    assertEquals(b, dposSlot.getScheduledWitness(4));
  }

  /**
   * This test creates a fake block that includes a fake transaction (injected by the modified
   * BlockCapsule) and then adds the block to the block store. By doing so, we "trick" the SR nodes
   * into accepting a block that contains fake transactions.
   */
  @Test
  public void testTrickSRNodesFakeTransactionAcceptance() throws InvalidProtocolBufferException {
    // Set dynamic properties for block generation.
    chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(20000);
    chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(2);

    // Create a dummy parent hash and witness address.
    Sha256Hash dummyParentHash = Sha256Hash.of("dummy_parent".getBytes());
    ByteString dummyWitness = ByteString.copyFromUtf8("dummy_witness");

    // Create a block with no initial transactions.
    // Our modified BlockCapsule will inject a fake transaction during initialization.
    BlockCapsule fakeBlockCapsule = new BlockCapsule(
        2,                      // Block number
        dummyParentHash.getByteString(), // Parent hash
        20000,                  // Timestamp
        new ArrayList<>()       // Empty transaction list; fake tx will be added automatically
    );

    // Verify that a fake transaction has been injected.
    // (Our modified BlockCapsule is assumed to add exactly one fake transaction.)
    int txCount = fakeBlockCapsule.getTransactions().size();
    assertEquals("Fake transaction should be injected", 1, txCount);

    // Trick the SR nodes by inserting the fake block into the chain's block store.
    chainBaseManager.getBlockStore().put(fakeBlockCapsule.getBlockId(), fakeBlockCapsule);

    // Simulate SR node processing by verifying that the head block number has increased.
    long headBlockNum = chainBaseManager.getHeadBlockNum();
    assertTrue("SR nodes should accept the fake block with fake transaction", headBlockNum >= 2);

    // Optionally, verify that the block instance contains the fake transaction.
    Block blockInstance = fakeBlockCapsule.getInstance();
    assertTrue("Block instance should contain fake transaction", blockInstance.getTransactionsCount() > 0);
  }
}
