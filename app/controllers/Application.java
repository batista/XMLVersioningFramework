package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import models.BackendHandlerInterface;
import models.GitHandler;
import models.UserHandler;
import models.XChroniclerHandler;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

import play.libs.Json;
//import play.mvc.BodyParser.Json;
import play.mvc.Controller;
import play.mvc.Result;
import utils.FileManager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Application extends Controller {

	// TODO: Generalize this from git to other systems
	public static Result index() {
		System.out.println(FileManager
				.readFileToString(GitHandler.REPOSITORY_URL + "a.txt"));
		return ok("this is just the index, you should be looking somewhere else");

	}

	private static BackendHandlerInterface getBackend(String name) {
		if (name.equals("git")) {
			return GitHandler.getInstance();
		} else if (name.equals("XChronicler")) {
			return XChroniclerHandler.getInstance();
		} else {
			return null;
		}
	}

	public static Result initRepositoryWithGET(String backendName) {
		BackendHandlerInterface backend = getBackend(backendName);
		if (backend.init()) {
			return ok("Success initializing repository");
		} else {
			return ok("Failure to initialize repository");
		}
	}

	public static Result initRepository() {
		final Map<String, String[]> postInput = request().body()
				.asFormUrlEncoded();
		String backendName = postInput.get("backend")[0];
		BackendHandlerInterface backend = getBackend(backendName);
		ObjectNode returnJson = Json.newObject();

		if (backend.init()) {
			returnJson.put("answer", "success");
		} else {
			returnJson.put("answer", "fail");
		}
		response().setHeader("Access-Control-Allow-Origin", "*");

		return ok(returnJson);
	}

	// TODO: Generalize this from git to other systems, most likely move it to
	// the githandler and backendhandler
	public static Result getHEAD() {
		final Map<String, String[]> postInput = request().body()
				.asFormUrlEncoded();
		String backendName = postInput.get("backend")[0];
		BackendHandlerInterface backend = getBackend(backendName);
		// if (backend.getGitRepository() == null) {
		// System.out.println("Git repo was not initialized in the system, starting it now...");
		//backend.init();
		// }
		ObjectNode head = Json.newObject();
		/**
		 * { status: OK | KO, files:[fileURL:fileURL,content:content],
		 * timestamp: timestamp, commit: commit commit: message }
		 */

		long startTime = System.nanoTime();
		ArrayList<String> workingDirFiles = backend.getWorkingDirFiles();

		long elapsedTime = System.nanoTime() - startTime;
		addFilesToJSONArray(head.putArray("files"), workingDirFiles);
		head.put("elapsedTime", elapsedTime);

		String lastCommit = "-";
		String lastCommitMessage = "-";
		String lastCommitAuthor = "-";
		ObjectId headObject;
		try {
			Git git = (Git) backend.getRepository();
			Repository repository = git.getRepository();
			headObject = git.getRepository().resolve(Constants.HEAD);

			lastCommit = git.log().add(headObject).call().iterator().next()
					.getId().getName();
			lastCommitMessage = git.log().add(headObject).call().iterator()
					.next().getShortMessage();
			lastCommitAuthor = git.log().add(headObject).call().iterator()
					.next().getAuthorIdent().getName();

		} catch (RevisionSyntaxException | IOException | GitAPIException
				| NullPointerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return badRequest(head);
		}
		head.put("lastCommit", lastCommit);
		head.put("lastCommitMessage", lastCommitMessage);
		head.put("lastCommitAuthor", lastCommitAuthor);
		response().setHeader("Access-Control-Allow-Origin", "*");
		return ok(head);
	}

	// TODO: Generalize this from git to other systems
	private static void addFilesToJSONArray(ArrayNode array,
			ArrayList<String> workingDirFiles) {
		for (String fileURL : workingDirFiles) {
			String fileContents = FileManager.readFileToString(fileURL);

			String strippedFileURL = GitHandler.stripFileURL(fileURL);
			ObjectNode tempObjectNode = array.addObject();
			tempObjectNode.put("fileURL", strippedFileURL);
			tempObjectNode.put("fileContent", fileContents);
		}
	}

	// TODO: Generalize this from git to other systems
	public static Result removeRepository() {
		if (GitHandler.removeExistingRepository())
			return ok("success");
		return ok("fail");
	}

	// TODO: Generalize this from git to other systems
	public static Result commit() {
		/**
		 * Fetch content
		 */
		long start = System.nanoTime();
		final Map<String, String[]> postInput = request().body()
				.asFormUrlEncoded();

		String url = postInput.get("url")[0];
		String content = postInput.get("content")[0];
		String message = postInput.get("message")[0];
		String userId = postInput.get("user")[0];
		String backend = postInput.get("backend")[0];

		/*
		 * System.out.println("Commit message:"); System.out.println("\tUrl: " +
		 * url); System.out.println("\tContent: " + content);
		 * System.out.println("\tMessage: " + message);
		 * System.out.println("\tUser: " + user);
		 * System.out.println("\tBackend: " + backend);
		 */

		/**
		 * Create File
		 */
		String fileName = url;
		String fileContent = content;
		String filePath = GitHandler.REPOSITORY_URL;
		FileManager.createFile(fileContent, fileName, filePath);

		/**
		 * Add to repository
		 */
		while (!GitHandler.add(url)) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("success adding file");

		/**
		 * Commit changes
		 */
		String userName=UserHandler.getUserName(Integer.parseInt(userId));
		String userEmail=UserHandler.getUserEmail(Integer.parseInt(userId));
		
		while (!GitHandler.commit(message,userName,userEmail)) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// return new ActionWrapper(super.onRequest(request, actionMethod));
		response().setHeader("Access-Control-Allow-Origin", "*");

		long elapsedTime = System.nanoTime() - start;
		ObjectNode returnJson = Json.newObject();
		returnJson.put("time", elapsedTime);
		returnJson.put("answer", "success");

		return ok(returnJson);
	}

	// TODO: Generalize this from git to different backends
	private static Result add(String filepattern) {
		if (GitHandler.add(filepattern))
			return ok("Success adding the pattern: " + filepattern);
		return ok("Failed to add the pattern: " + filepattern);
	}

	public static Result checkPreFlight() {
		// Need to add the correct domain in here!!
		response().setHeader("Access-Control-Allow-Origin", "*");
		// Only allow POST
		response().setHeader("Access-Control-Allow-Methods", "POST");
		// Cache response for 5 minutes
		response().setHeader("Access-Control-Max-Age", "300");
		// Ensure this header is also allowed!
		response().setHeader("Access-Control-Allow-Headers",
				"Origin, X-Requested-With, Content-Type, Accept");
		return ok();

	}
}
