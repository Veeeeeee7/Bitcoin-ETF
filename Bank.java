import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Level;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Bank {

    // Constants and Global Variables
    private static final String WALLET_FILE_NAME = "bitcoin-wallet";
    private static final String SECOND_WALLET_FILE_NAME = "second-wallet";
    private static final NetworkParameters params = TestNet3Params.get();
    private static final long RECENT_PERIOD = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    private static List<Transaction> recentTransactions = new ArrayList<>();
    private static WalletAppKit walletAppKit = null;
    private static int count = 0;

    static {
        // Assuming SLF4J is bound to logback
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.ERROR);
    }

    public static void main(String[] args) throws Exception {
        // Wallet setup
        Wallet wallet = checkOrCreateWallet(params, WALLET_FILE_NAME);

        // Event listener for transactions
        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                long currentTime = System.currentTimeMillis();
                long transactionTime = tx.getUpdateTime().getTime();
                if (currentTime - transactionTime <= RECENT_PERIOD) {
                    recentTransactions.add(tx);
                    System.out.println("New recent transaction: " + tx.getHashAsString());
                }
            }
        });

        // Initial setup output
        printWalletAndConnectionInfo(wallet, WALLET_FILE_NAME);

        System.out.println("\n");
        Wallet secondWallet = checkOrCreateWallet(params, SECOND_WALLET_FILE_NAME);
        secondWallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                long currentTime = System.currentTimeMillis();
                long transactionTime = tx.getUpdateTime().getTime();
                if (currentTime - transactionTime <= RECENT_PERIOD) {
                    recentTransactions.add(tx);
                    System.out.println("New recent transaction: " + tx.getHashAsString());
                }
            }
        });
        printWalletAndConnectionInfo(secondWallet, SECOND_WALLET_FILE_NAME);

        // Continuous balance check loop
        while (true) {
            // Coin amountToSend = Coin.parseCoin("0.00001");
            // Address destinationAddress =
            // secondWalletAppKit.wallet().currentReceiveAddress();
            // Transaction transaction =
            // walletAppKit.wallet().createSend(destinationAddress, amountToSend);

            // // Sign and commit the transaction
            // walletAppKit.wallet().commitTx(transaction);

            // // Broadcast the transaction to the network
            // walletAppKit.peerGroup().broadcastTransaction(transaction);
            // System.out.println("\nCreated transaction for 0.00001 BTC\n");

            System.out.println();
            System.out.println(WALLET_FILE_NAME + " : " + wallet.currentReceiveAddress()
                    + " : balance (in satoshis): " + wallet.getBalance().value);
            System.out.println(SECOND_WALLET_FILE_NAME + " : " + secondWallet.currentReceiveAddress()
                    + " : balance (in satoshis): " + secondWallet.getBalance().value);
            System.out.println("Block height: " + walletAppKit.chain().getBestChainHeight());
            System.out.println("Peers: " + walletAppKit.peerGroup().getConnectedPeers().size());

            // Optionally, clean up old transactions from the list
            long currentTime = System.currentTimeMillis();
            recentTransactions.removeIf(tx -> currentTime - tx.getUpdateTime().getTime() > RECENT_PERIOD);

            // Create a transaction
            if (count < 3 && wallet.getBalance().value > 0) {
                transferFunds(wallet, secondWallet, Coin.parseCoin("0.00001"));
                count++;
            }

            TimeUnit.SECONDS.sleep(10); // Adjust check interval as needed
        }

    }

    private static void transferFunds(Wallet sourceWallet, Wallet destinationWallet, Coin amount) throws Exception {
        System.out.println("\nSending " + amount.toFriendlyString() + " to "
                + destinationWallet.currentReceiveAddress().toString());
        SendRequest request = SendRequest.to(destinationWallet.currentReceiveAddress(), amount);
        request.ensureMinRequiredFee = true;
        request.feePerKb = Transaction.DEFAULT_TX_FEE;
        Wallet.SendResult sendResult = sourceWallet.sendCoins(walletAppKit.peerGroup(), request);
        System.out.println("Transaction hash: " + sendResult.tx.getTxId());
    }

    // Helper Functions
    private static Wallet checkOrCreateWallet(NetworkParameters params, String walletFileName)
            throws IOException, UnreadableWalletException {

        walletAppKit = new WalletAppKit(params, new File("."), walletFileName);
        walletAppKit.setBlockingStartup(false);
        walletAppKit.startAsync();
        walletAppKit.awaitRunning();
        walletAppKit.peerGroup().setBloomFilterFalsePositiveRate(0.001); // Example: 0.1% false positive rate

        // System.out
        // .println(walletFileName + " address: " +
        // walletAppKit.wallet().currentReceiveAddress().toString());

        File walletFile = new File(walletFileName + ".wallet");

        if (walletFile.exists()) {
            // Wallet exists, load it
            return Wallet.loadFromFile(walletFile);
        } else {
            Wallet wallet = walletAppKit.wallet();
            wallet.saveToFile(walletFile);
            throw new UnreadableWalletException("Wallet not found, created a new one");
        }

    }

    private static void printWalletAndConnectionInfo(Wallet wallet, String walletFileName) {
        System.out.println("Initial Balance: " + wallet.getBalance().toFriendlyString());
        System.out.println("Network: " + params.getId());
        System.out.println("Connected peers: " + walletAppKit.peerGroup().getConnectedPeers().size());
        System.out.println(walletFileName + " address: " + wallet.currentReceiveAddress().toString());
        System.out.println("Block height: " + walletAppKit.chain().getBestChainHeight());
    }
}