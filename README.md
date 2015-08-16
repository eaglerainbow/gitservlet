# gitservlet

## Concept
gitservlet is a J2EE servlet built with the Maven Infrastructure which allows to store the web resources in git repositories. The primary intention for this is to provide an easy-to-use, simple service to provide artifacts to consumers, which are subject to versioning.

## First Example

Let us assume the following environment:
* You are running gitservlet with context */gitservlet* on your local J2EE webserver, which is available at *http://localhost:8080*.
* You have a local git repository at *C:\repobase\gitrepo*.
* You have configured gitservlet in such a way that *C:\repobase* is being used as the RepoBase.
* This git repository has a branch called *master*.
* On that branch *master* you have committed mutliple files, amongst one which is called *LICENSE*.

In this situation you will be able to access the LICENSE file via the URL *http://localhost:8080/gitrepo/master/LICENSE*.

## Second Example
Assume that the situation is as described above. However, additionally, while neither the J2EE webserver nor the servlet has been stopped, you are creating a new commit, changing the contents of the *LICENSE* file, thus creating a new commit. You access the same URL as denoted above. You will receive the new version of the file.

## Third Example
In the directory *C:\repobase* you create another git repository called *secondrepo*. Thus, it is available at *C:\repobase\secondrepo*. You commit a file called *readme.txt* to this new repository.
Note that you still did not stop the J2EE server nor was the servlet stopped as well. 

Via the URL *http://localhost:8080/secondrepo/master/readme.txt* are able to access the contents of this new file as well. The servlet has automatically discovered the new repository and is streaming it based on inbound requests.

## Benefits
tbd

## Why not using github for this?
Github with its diverse raw- and preview features is capable of providing a very similiar set of features. Therefore, theoretically, it would be possible to also use the github infrastructure to perform the same steps. However, there are two major reasons for creating the servlet:
1. Installing Github is a very heavy-weight activity and incorporates numerous external dependendies. 
2. Gihub is intended (and therefore optimized) for source-code repository streaming and not for versioned deployment-like scenarios of compiled/build artifacts.
