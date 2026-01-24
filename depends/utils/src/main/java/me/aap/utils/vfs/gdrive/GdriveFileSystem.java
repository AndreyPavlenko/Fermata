package me.aap.utils.vfs.gdrive;

import android.content.Intent;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.security.auth.login.LoginException;

import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.Completable;
import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.CheckedFunction;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.resource.Rid;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
// TODO: Implement
public class GdriveFileSystem implements VirtualFileSystem {
	public static final String SCHEME_GDRIVE = "gdrive";
	static final String FOLDER_MIME = "application/vnd.google-apps.folder";
	private final Provider provider;
	private final String requestToken;
	private final Supplier<FutureSupplier<? extends AppActivity>> activitySupplier;
	@Keep
	private final FutureRef<Drive> drive = FutureRef.create(this::createDrive);
	private String email = "somebody@gmail.com";

	private GdriveFileSystem(Provider provider, String requestToken, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier) {
		this.provider = provider;
		this.requestToken = requestToken;
		this.activitySupplier = activitySupplier;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@NonNull
	@Override
	public FutureSupplier<List<VirtualFolder>> getRoots() {
		return useDrive(d -> {
			Drive.Files.List req = d.files().list()
					.setQ("'root' in parents and mimeType = '" + FOLDER_MIME + "' and trashed = false")
					.setSpaces("drive")
					.setFields("nextPageToken, files(id, name)");
			return (List) loadList(req, null, true);
		});
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualResource> getResource(Rid rid) {
		return useDrive(d -> {
			String id = rid.getPath().substring(1);
			File f = d.files().get(id).setFields("name, mimeType").execute();

			if (FOLDER_MIME.equals(f.getMimeType())) {
				return new GdriveFolder(this, id, f.getName());
			} else {
				return new GdriveFile(this, id, f.getName());
			}
		});
	}

	@Override
	public FutureSupplier<VirtualFile> getFile(Rid rid) {
		String id = rid.getPath().substring(1);
		String name = rid.getFragment();
		return completed(new GdriveFile(this, id, name));
	}

	@Override
	public FutureSupplier<VirtualFolder> getFolder(Rid rid) {
		String id = rid.getPath().substring(1);
		String name = rid.getFragment();
		return completed(new GdriveFolder(this, id, name));
	}

	<T> FutureSupplier<T> useDrive(CheckedFunction<Drive, T, Throwable> func) {
		return drive.get().then(d -> Async.retry(() -> App.get().execute(() -> func.apply(d)),
				ex -> {
					if (ex instanceof UserRecoverableAuthException) {
						return activitySupplier.get().get()
								.startActivityForResult(((UserRecoverableAuthException) ex)::getIntent)
								.then(i -> App.get().execute(() -> func.apply(d)));
					} else if (ex instanceof UserRecoverableAuthIOException) {
						return activitySupplier.get().get()
								.startActivityForResult(((UserRecoverableAuthIOException) ex)::getIntent)
								.then(i -> App.get().execute(() -> func.apply(d)));
					} else {
						return failed(ex);
					}
				}));
	}

	private FutureSupplier<Drive> createDrive() {
		GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(App.get());
		if (account != null) return completed(createDrive(account));

		Promise<Drive> p = new Promise<>();
		activitySupplier.get().onFailure(p::completeExceptionally).onSuccess(a -> signIn(p, a));
		return p;
	}

	private Drive createDrive(GoogleSignInAccount account) {
		App app = App.get();
		GoogleAccountCredential c = GoogleAccountCredential
				.usingOAuth2(app, Collections.singleton(DriveScopes.DRIVE));
		c.setSelectedAccount(account.getAccount());
		email = account.getEmail();
		return new Drive.Builder(new NetHttpTransport(), new GsonFactory(), c)
				.setApplicationName(app.getPackageName()).build();
	}

	private void signIn(Promise<Drive> p, AppActivity activity) {
		GoogleSignInOptions o = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestIdToken(requestToken).requestEmail().build();
		GoogleSignInClient client = GoogleSignIn.getClient(activity.getContext(), o);
		activity.startActivityForResult(client::getSignInIntent).onCompletion((r, f) -> {
			if (f != null) {
				Log.e(f);
				p.completeExceptionally(new LoginException("Google sign in failed"));
			} else {
				handleSignInResult(p, r);
			}
		});
	}

	private void handleSignInResult(Completable<Drive> p, Intent result) {
		GoogleSignIn.getSignedInAccountFromIntent(result).addOnSuccessListener(account -> {
			Log.d("Signed in as ", account.getEmail());
			try {
				p.complete(createDrive(account));
			} catch (Exception ex) {
				Log.e(ex, "Failed to create drive");
				p.completeExceptionally(ex);
			}
		}).addOnFailureListener(ex -> {
			Log.e(ex, "Google sign in failed");
			p.completeExceptionally(ex);
		});
	}

	@NonNull
	@Override
	public Provider getProvider() {
		return provider;
	}

	String getEmail() {
		return email;
	}

	List<VirtualResource> loadList(Drive.Files.List req, GdriveFolder parent, boolean dirsOnly) throws IOException {
		List<VirtualResource> ls = null;

		for (; ; ) {
			FileList list = req.execute();
			List<File> files = list.getFiles();
			if (ls == null) ls = new ArrayList<>(files.size());

			for (File f : files) {
				if (dirsOnly || FOLDER_MIME.equals(f.getMimeType())) {
					ls.add(new GdriveFolder(this, f.getId(), f.getName(), parent));
				} else {
					ls.add(new GdriveFile(this, f.getId(), f.getName(), parent));
				}
			}

			String next = list.getNextPageToken();
			if (next == null) break;
			list.setNextPageToken(next);
		}

		return ls;
	}

	public static class Provider implements VirtualFileSystem.Provider {
		public static final Pref<Supplier<String>> GOOGLE_TOKEN = Pref.s("GOOGLE_TOKEN", "");
		private static final Set<String> scheme = Collections.singleton(SCHEME_GDRIVE);
		private final Supplier<FutureSupplier<? extends AppActivity>> activitySupplier;

		public Provider(Supplier<FutureSupplier<? extends AppActivity>> activitySupplier) {
			this.activitySupplier = activitySupplier;
		}

		@NonNull
		@Override
		public Set<String> getSupportedSchemes() {
			return scheme;
		}

		@NonNull
		@Override
		public FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps) {
			return completed(new GdriveFileSystem(this, ps.getStringPref(GOOGLE_TOKEN), activitySupplier));
		}
	}
}
