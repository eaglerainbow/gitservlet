# gitservlet

## Concept
gitservlet is a J2EE servlet built with the Maven Infrastructure which allows to store the web resources in git repositories. The primary intention for this is to provide an easy-to-use, simple service to provide artifacts to consumers, which are subject to versioning.

## Examples
### First Example

Let us assume the following environment:
* You are running gitservlet with context */gitservlet* on your local J2EE webserver, which is available at *http://localhost:8080*.
* You have a local git repository at *C:\repobase\gitrepo*.
* You have configured gitservlet in such a way that *C:\repobase* is being used as the RepoBase.
* This git repository has a branch called *master*.
* On that branch *master* you have committed mutliple files, amongst one which is called *LICENSE*.

In this situation you will be able to access the LICENSE file via the URL *http://localhost:8080/gitrepo/master/LICENSE*.

### Second Example
Assume that the situation is as described above. However, additionally, while neither the J2EE webserver nor the servlet has been stopped, you are creating a new commit, changing the contents of the *LICENSE* file, thus creating a new commit. You access the same URL as denoted above. You will receive the new version of the file.

### Third Example
In the directory *C:\repobase* you create another git repository called *secondrepo*. Thus, it is available at *C:\repobase\secondrepo*. You commit a file called *readme.txt* to this new repository.
Note that you still did not stop the J2EE server nor was the servlet stopped as well. 

Via the URL *http://localhost:8080/secondrepo/master/readme.txt* are able to access the contents of this new file as well. The servlet has automatically discovered the new repository and is streaming it based on inbound requests.

### Fourth Example
In the *secondrepo* you are creating a new branch called *newbranch*. You do some modifications to the *readme.txt* file. Once committed, you may immediately access the new variant of the file via the URL *http://localhost:8080/secondrepo/newbranch/readme.txt*. Yet, *http://localhost:8080/secondrepo/master/readme.txt* still shows the old version of the file. 
Note that in *secondrepo* you still have *newbranch* checked out. 

### Fifth Example
In the *secondrepo* you are creating a new tag called *oldversion*. Stil being on the branch *newbranch* you are make some further modifications to the file *readme.txt*.
The modified version of *readme.txt* is available at *http://localhost:8080/secondrepo/newbranch/readme.txt*. The previous version of the file *readme.txt* can still be accessed via *http://localhost:8080/secondrepo/oldversion/readme.txt*, as tags can be used as substitudes of branches.

## Benefits
* it's a lightweight servlet with close to no external dependencies (or to be more precise: the dependencies are already bundled with the servlet ==> *self-contained*)
* Access to versioned data is extreme fast; yet, you may leverage the extremely efficient storage concept of git to reduce the amount of disk space required for each version.
* Publishing of content is as easy as committing changes to a git repository. You may use the git Remote API to pushed your changes to the public git repository.
* Both branches and tags can be used to refer to versions (i.e. commits).

## Why not using github for this?
Github with its diverse raw- and preview features is capable of providing a very similiar set of features. Therefore, theoretically, it would be possible to also use the github infrastructure to perform the same steps. However, there are two major reasons for creating the servlet:

1. Installing Github is a very heavy-weight activity and incorporates numerous external dependendies. 
2. Gihub is intended (and therefore optimized) for source-code repository streaming and not for versioned deployment-like scenarios of compiled/build artifacts.

## Performance Analysis
A first brief performance analysis has yielded the following results:

| Scenario | Processing Time |
|:---------|----------------:|
| First access after start (includes initialization of JGit Library) | 6651 ms |
| Second access to same file | 10 ms |
| Different file in a newer commit (same repository) | 11 ms |
| Copying the same repository within the RepoBase; access to copied repository | 8 ms |

The environment was as follows:
* Intel Core i7-3770
* Windows 7 SP1
* JDK 1.8.0_20
* Tomcat 8.0.24
* RepoBase was on a RAM drive (thus, close to no time for disk access to be expected in the figures above).
* Version of gitservlet under investigation: commit 76f257a7ba3868a4b7789d7d5e2815d099e76c9f
