package gtfu.tools;

import gtfu.Debug;
import gtfu.Util;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.GHContentBuilder;

import java.util.Date;

/**
* Interact with Github
*/
public class GitHubUtil {
    private static final String GH_ACCESS_TOKEN = System.getenv("GH_ACCESS_TOKEN");
    private static final String GH_ORG_NAME = "cal-itp";
    private static final String PROJECT_NAME = "graas";
    private static final String MAIN_BRANCH_NAME = "main";
    private String mainBranchHash;
    private GHRepository repo;

    /**
    * Initial setup
    */
    public GitHubUtil() throws Exception {
        GitHub github = new GitHubBuilder().withOAuthToken(GH_ACCESS_TOKEN, GH_ORG_NAME).build();
        repo = github.getRepository(GH_ORG_NAME + "/" + PROJECT_NAME);
        GHBranch main =repo.getBranch(MAIN_BRANCH_NAME);
        mainBranchHash = main.getSHA1();
    }

    /**
    * Return the latest commit timestamp in milliseconds for a provided file
    * @param fileName   file name including full path
    * @return           timestmap millis for the latest commit
    */
    public long getLatestCommitMillis(String fileName) throws Exception {

        GHCommitQueryBuilder queryBuilder = repo.queryCommits().from(mainBranchHash).path(fileName);
        PagedIterable<GHCommit> commits = queryBuilder.list();
        PagedIterator<GHCommit> iterator = commits.iterator();

        Date latestCommit = new Date(Long.MIN_VALUE);
        int commitCount = 0;
        while (iterator.hasNext()) {
            commitCount++;
            GHCommit commit = iterator.next();
            Date commitDate = commit.getCommitDate();

            if (commitDate.after(latestCommit)){
                latestCommit = commitDate;
            }
        }
        if (commitCount == 0){
            System.err.println("** File " + fileName + " does not exist. exiting.");
            System.exit(1);
        }
        return latestCommit.getTime();
    }

    private void createCommit(String branchName, String message, String path, byte[] file) throws Exception {
        GHRef main = repo.getRef("heads/" + MAIN_BRANCH_NAME);
        GHCommit latestCommit = repo.getCommit(main.getObject().getSha());
        GHTreeBuilder treeBuilder = repo.createTree();
        treeBuilder.baseTree(latestCommit.getTree().getSha());
        treeBuilder.add(path, file, false);
        GHTree tree = treeBuilder.create();
        GHCommit commit = repo.createCommit()
                .parent(latestCommit.getSHA1())
                .tree(tree.getSha())
                .message(message)
                .create();
        GHRef branch = repo.createRef("refs/heads/" + branchName, commit.getSHA1());
    }

    private void createPR(String title, String branchName, String description) throws Exception {
        GHPullRequest pr = repo.createPullRequest(title, branchName, MAIN_BRANCH_NAME, description);
    }

    /**
    * Create a commit and PR, with the provided file and paramaters
    * Currently this function can only create/update a single file
    * @param title          Title for the PR
    * @param description    Description for the PR
    * @param path           Path for the file to be updated/created.
    * @param file           Byte array representation of file
    * @param message        Commit message
    * @param branchName     Name for new branch
    */
    public void createCommitAndPR(String title, String description, String path, byte[] file, String message, String branchName) throws Exception {
        createCommit(branchName, message, path, file);
        createPR(title, branchName, description);
    }
}