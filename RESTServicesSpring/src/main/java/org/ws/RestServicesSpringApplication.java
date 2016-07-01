package org.ws;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.VndErrors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.ws.bookmarks.Account;
import org.ws.bookmarks.AccountRepository;
import org.ws.bookmarks.Bookmark;
import org.ws.bookmarks.BookmarkRepository;

@SpringBootApplication
public class RestServicesSpringApplication {
	
	@Bean
	CommandLineRunner init(AccountRepository accountRespository, BookmarkRepository bookmarkRepository) {
		
		return (evt) -> Arrays.asList(
						"jhoeller,dsyer,pwebb,orgierke,rwinch,mfisher,mpollack,jlong".split(","))
						.forEach(
									a -> {
											Account account = accountRespository.save(new Account(a, "password"));
											bookmarkRepository.save(new Bookmark(account, "http://bookmark.com/1/" + a, "A description"));
											bookmarkRepository.save(new Bookmark(account, "http://bookmark.com/2/" + a, "A description"));
									});
				
	}
	
	public static void main(String[] args) {
		SpringApplication.run(RestServicesSpringApplication.class, args);
	}
	
}

class BookmarkResource extends ResourceSupport {
	
	private final Bookmark bookmark;
	
	public BookmarkResource(Bookmark bookmark) {
		String username = bookmark.getAccount().getUsername();
		this.bookmark = bookmark;
		this.add(new Link(bookmark.getUri(), "bookmark-uri"));
		this.add(linkTo(BookmarkRestController.class, username).withRel("bookmarks"));
		this.add(linkTo(methodOn(BookmarkRestController.class, username).readBookmark(username, bookmark.getId())).withSelfRel());
	}
	
	public Bookmark getBookmark() {
		return bookmark;
	}
	
}

@RestController
@RequestMapping("/{userId}/bookmarks")
class BookmarkRestController {
	
	private final BookmarkRepository bookmarkRepository;

	private final AccountRepository accountRepository;
	
	@RequestMapping(method=RequestMethod.POST)
	ResponseEntity<?> add(@PathVariable String userId, @RequestBody Bookmark input) {
		
		this.validateUser(userId);
		return this.accountRepository
				.findByUsername(userId)
				.map(account -> {
					Bookmark result = bookmarkRepository.save(new Bookmark(account, input.uri, input.description));
					
					HttpHeaders httpHeaders = new HttpHeaders();
					httpHeaders.setLocation(ServletUriComponentsBuilder
							.fromCurrentRequest().path("/{id}")
							.buildAndExpand(result.getId()).toUri());
					return new ResponseEntity<>(null, httpHeaders, HttpStatus.CREATED);
				}).get();
		
	}
	
	@RequestMapping(value = "/{bookmarkId}", method = RequestMethod.GET)
	Bookmark readBookmark(@PathVariable String userId, @PathVariable Long bookmarkId) {
		
		this.validateUser(userId);
		return this.bookmarkRepository.findOne(bookmarkId);
		
	}
	
	@RequestMapping(method = RequestMethod.GET)
	Resources<BookmarkResource> readBookmarks(@PathVariable String userId) {
		
		this.validateUser(userId);
		
		List<BookmarkResource> bookmarkResourceList = bookmarkRepository.findByAccountUsername(userId)
				.stream()
				.map(BookmarkResource::new)
				.collect(Collectors.toList());
		return new Resources<BookmarkResource>(bookmarkResourceList);
		
	}
	
	@Autowired
	BookmarkRestController(BookmarkRepository bookmarkRepository, AccountRepository accountRepository) {
		
		this.bookmarkRepository = bookmarkRepository;
		this.accountRepository = accountRepository;
		
	}
	
	private void validateUser(String userId) {
		this.accountRepository.findByUsername(userId).orElseThrow(
				() -> new UserNotFoundException(userId));
	}
	
}

@ControllerAdvice
class BookmarkControllerAdvice {
	
	@ResponseBody
	@ExceptionHandler(UserNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	VndErrors userNotFoundExceptionHandler(UserNotFoundException ex) {
		return new VndErrors("error", ex.getMessage());
	}
	
}

class UserNotFoundException extends RuntimeException {
	
	public UserNotFoundException(String userId) {
		super("could not find user '" + userId + "'.");
	}
	
}
