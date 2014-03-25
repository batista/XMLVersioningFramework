package models;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.inject.Provider;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.w3c.dom.Document;

import se.repos.vfile.VFileCalculatorImpl;
import se.repos.vfile.VFileCommitHandler;
import se.repos.vfile.VFileCommitItemHandler;
import se.repos.vfile.VFileDocumentBuilderFactory;
import se.repos.vfile.gen.VFile;
import se.repos.vfile.store.VFileStore;
import se.repos.vfile.store.VFileStoreDisk;
import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsChangesetReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsContentsReaderSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.SvnlookClientProviderStateless;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.impl.CmsItemIdUrl;

public class XChroniclerHandler extends BackendHandlerInterface {
		private boolean doCleanup = false;
		private File testDir = null;
		private File repoDir = null;
		private SVNURL repoUrl;
		private File wc = null;
		
		private SVNClientManager clientManager = null;
		private Provider<SVNLookClient> svnlookProvider = new SvnlookClientProviderStateless();
		private static XChroniclerHandler instance=null;
		
		private XChroniclerHandler(){
			
		}
		
		public static BackendHandlerInterface getInstance(){
			if(instance==null){
				instance=new XChroniclerHandler();
			}
			return instance;
		}
	  public void setup(){
		  try {
			this.testDir = File.createTempFile("test-" + this.getClass().getName(), "");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	        this.testDir.delete();
	        this.repoDir = new File(this.testDir, "repo");
	        try {
				this.repoUrl = SVNRepositoryFactory.createLocalRepository(this.repoDir, true,
				        false);
			} catch (SVNException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        // for low level operations
	        // SVNRepository repo = SVNRepositoryFactory.create(repoUrl);
	        this.wc = new File(this.testDir, "wc");
	        System.out.println("Running local fs repository " + this.repoUrl);
	        this.clientManager = SVNClientManager.newInstance();
	  }
	  
	  
	public void oldTest(){
		CmsRepository repository = new CmsRepository("/anyparent", "anyname");
	    CmsItemId testID = new CmsItemIdUrl(repository, new CmsItemPath("/basic.xml"));
	    VFileStore store=null;
		try {
			store = this.testVFiling(testID, new File(
			        "src/test/resources/se/repos/vfile"), "/basic_1.xml", "basic_2.xml",
			        "basic_3.xml");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	    Document document = store.get(testID);
	}
    
    
    

    /*
     * Takes a series of file paths, runs unit test that asserts they can be
     * v-filed.
     */
    private VFileStore testVFiling(CmsItemId testID, File folder, String... filePaths)
            throws Exception {

        // Parse the files as Documents for data integrity checking.
        DocumentBuilder db = new VFileDocumentBuilderFactory().newDocumentBuilder();
        ArrayList<Document> documents = new ArrayList<Document>();
        for (String filePath : filePaths) {
            Document d = db.parse(new File(folder, filePath));
            documents.add(d);
        }

        CmsRepositorySvn repository = new CmsRepositorySvn(testID.getRepository()
                .getParentPath(), testID.getRepository().getName(), this.repoDir);
        CmsContentsReaderSvnkitLook contentsReader = new CmsContentsReaderSvnkitLook();
        contentsReader.setSVNLookClientProvider(this.svnlookProvider);
        CmsChangesetReaderSvnkitLook changesetReader = new CmsChangesetReaderSvnkitLook();
        changesetReader.setSVNLookClientProvider(this.svnlookProvider);

        this.svncheckout();

        ArrayList<RepoRevision> revisions = new ArrayList<RepoRevision>();

        File testFile = new File(this.wc, testID.getRelPath().getPath());
        boolean addedToSVN = false;

        // Commits all the files to SVN, saving the RepoRevisions of each commit.
        Transformer trans = TransformerFactory.newInstance().newTransformer();
        for (int i = 0; i < documents.size(); i++) {
            Document d = documents.get(i);
            Source source = new DOMSource(d);
            Result result = new StreamResult(testFile);
            trans.transform(source, result);
            if (!addedToSVN) {
                this.svnadd(testFile);
                addedToSVN = true;
            }
            RepoRevision svncommit = this.svncommit("");
            if (svncommit == null) {
                throw new RuntimeException("No diff for file " + filePaths[i]);
            }
            revisions.add(svncommit);
        }

        VFileStore store = new VFileStoreDisk("./vfilestore");
        VFileCalculatorImpl calculator = new VFileCalculatorImpl(store);

        VFileCommitItemHandler itemHandler = new VFileCommitItemHandler(calculator,
                contentsReader);
        VFileCommitHandler commitHandler = new VFileCommitHandler(repository, itemHandler)
                .setCmsChangesetReader(changesetReader);

        /*
         * For each revision, call V-Filing on the new file version, and assert
         * that the V-File is equal to the saved document.
         */
        for (int i = 0; i < documents.size(); i++) {
            commitHandler.onCommit(revisions.get(i));
            VFile v = new VFile(store.get(testID));
            Document d = documents.get(i);
            v.matchDocument(d);
        }

        return store;
    }

    private void svncheckout() throws SVNException {
        this.clientManager.getUpdateClient().doCheckout(this.repoUrl, this.wc,
                SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, false);
    }
    private void svnadd(File... paths) throws SVNException {
        this.clientManager.getWCClient().doAdd(paths, true, false, false,
                SVNDepth.INFINITY, true, true, true);
    }
    /**
     * @param comment
     * @return revision if committed, null if nothing to commit
     * @throws SVNException
     */
    private RepoRevision svncommit(String comment) throws SVNException {
        SVNCommitInfo info = this.clientManager.getCommitClient().doCommit(
                new File[] { this.wc }, false, comment, null, null, false, false,
                SVNDepth.INFINITY);
        long revision = info.getNewRevision();
        if (revision < 0L) {
            return null;
            // this.doCleanup = false;
            // throw new
            // RuntimeException("SVN returned negative version number. Working copy: "
            // + this.wc);
        }
        return new RepoRevision(revision, info.getDate());
    }
    
    
    

	
}