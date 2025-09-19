package com.guidepedia.service;

import com.guidepedia.exception.BusinessException;
import com.guidepedia.exception.MyEntityNotFoundException;
import com.guidepedia.model.entity.ArticleEntity;
import com.guidepedia.model.entity.CategoryEntity;
import com.guidepedia.model.entity.CommentEntity;
import com.guidepedia.model.entity.UserEntity;
import com.guidepedia.model.request.ArticleRequest;
import com.guidepedia.model.request.CommentRequest;
import com.guidepedia.model.response.ArticleResponse;
import com.guidepedia.model.response.CommentResponse;
import com.guidepedia.repo.ArticleRepository;
import com.guidepedia.repo.CategoryRepository;
import com.guidepedia.repo.CommentRepository;
import com.guidepedia.repo.UserRepository;
import com.guidepedia.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserDetailsImpl userDetails;

    @InjectMocks
    private ArticleService articleService;

    private UserEntity createUser(Long id, String login) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setLogin(login);
        u.setSavedArticles(new HashSet<>());
        u.setSubscribers(new HashSet<>());
        u.setSubscriptions(new HashSet<>());
        u.setArticlesReaction(new HashSet<>());
        return u;
    }

    private ArticleEntity createArticle(Long id, UserEntity author) {
        ArticleEntity a = new ArticleEntity();
        a.setId(id);
        a.setCreatedBy(author);
        a.setTitle("title " + id);
        a.setText("text");
        a.setDescription("desc");
        a.setCreatedAt(LocalDateTime.now());
        a.setUsers(new HashSet<>());
        a.setSavedUsers(new HashSet<>());
        return a;
    }

    private CategoryEntity createCategory(Integer id, String name) {
        CategoryEntity c = new CategoryEntity();
        c.setId(id);
        c.setName(name);
        return c;
    }

    @Test
    void createArticle_success_returnsArticleResponse() {
        Long userId = 1L;
        Integer categoryId = 10;
        String catName = "tech";

        UserEntity user = createUser(userId, "author");
        ArticleRequest req = mock(ArticleRequest.class);
        when(req.getCategoryName()).thenReturn(catName);
        when(req.getTitle()).thenReturn("T");
        when(req.getText()).thenReturn("X");
        when(req.getDescription()).thenReturn("D");
        when(req.getDraft()).thenReturn(false);

        CategoryEntity cat = createCategory(categoryId, catName);

        when(userService.getUser(userDetails)).thenReturn(user);
        when(categoryRepository.findByName(catName)).thenReturn(Optional.of(cat));
        when(articleRepository.save(any(ArticleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArticleResponse resp = articleService.createArticle(req, userDetails);

        assertThat(resp).isNotNull();
        verify(categoryRepository, times(1)).findByName(catName);
        verify(articleRepository, times(1)).save(any(ArticleEntity.class));
    }

    @Test
    void createArticle_categoryNotFound_throwsMyEntityNotFoundException() {
        ArticleRequest req = mock(ArticleRequest.class);
        when(req.getCategoryName()).thenReturn("no-such-cat");
        when(userService.getUser(userDetails)).thenReturn(createUser(2L, "u"));

        when(categoryRepository.findByName("no-such-cat")).thenReturn(Optional.empty());

        assertThrows(MyEntityNotFoundException.class, () -> articleService.createArticle(req, userDetails));
        verify(articleRepository, never()).save(any());
    }

    @Test
    void getArticleById_found_returnsArticleResponse() {
        Long articleId = 100L;
        UserEntity user = createUser(3L, "reader");
        ArticleEntity article = createArticle(articleId, createUser(4L, "aut"));

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(userService.getUser(userDetails)).thenReturn(user);

        ArticleResponse resp = articleService.getArticleById(articleId, userDetails);
        assertThat(resp).isNotNull();
        verify(articleRepository, times(1)).findById(articleId);
    }

    @Test
    void getArticleById_notFound_throwsMyEntityNotFoundException() {
        Long articleId = 999L;
        when(articleRepository.findById(articleId)).thenReturn(Optional.empty());
        assertThrows(MyEntityNotFoundException.class, () -> articleService.getArticleById(articleId, userDetails));
    }

    @Test
    void createReaction_addAndRemoveFlow_andSave() {
        Long articleId = 200L;
        UserEntity user = createUser(5L, "u5");
        ArticleEntity article = createArticle(articleId, createUser(6L, "author"));

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(userService.getUser(userDetails)).thenReturn(user);
        when(articleRepository.save(article)).thenReturn(article);

        var respAdd = articleService.createReaction(articleId, true, userDetails);
        assertThat(respAdd).isNotNull();
        assertThat(article.getUsers()).contains(user);
        assertThat(user.getArticlesReaction()).contains(article);

        var respRemove = articleService.createReaction(articleId, false, userDetails);
        assertThat(respRemove).isNotNull();
        assertThat(article.getUsers()).doesNotContain(user);
        assertThat(user.getArticlesReaction()).doesNotContain(article);

        verify(articleRepository, atLeast(2)).findById(articleId);
        verify(articleRepository, atLeast(2)).save(article);
    }

    @Test
    void createReaction_invalidTransition_throwsBusinessException() {
        Long articleId = 300L;
        UserEntity user = createUser(7L, "u7");
        ArticleEntity article = createArticle(articleId, createUser(8L, "aut"));

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(userService.getUser(userDetails)).thenReturn(user);

        assertThrows(BusinessException.class, () -> articleService.createReaction(articleId, false, userDetails));

        article.getUsers().add(user);
        user.getArticlesReaction().add(article);

        assertThrows(BusinessException.class, () -> articleService.createReaction(articleId, true, userDetails));
    }

    @Test
    void createComment_success_returnsCommentResponse() {
        Long articleId = 400L;
        UserEntity user = createUser(9L, "commenter");
        ArticleEntity article = createArticle(articleId, createUser(10L, "author"));

        CommentRequest req = mock(CommentRequest.class);
        when(req.getComment()).thenReturn("Nice!");

        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(userService.getUser(userDetails)).thenReturn(user);
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CommentResponse resp = articleService.createComment(articleId, userDetails, req);
        assertThat(resp).isNotNull();
        verify(commentRepository, times(1)).save(any(CommentEntity.class));
    }

    @Test
    void getCountReactions_returnsCorrectNumber() {
        Long articleId = 500L;
        UserEntity current = createUser(11L, "curr");
        ArticleEntity article = createArticle(articleId, createUser(12L, "aut"));

        UserEntity r1 = createUser(21L, "r1");
        UserEntity r2 = createUser(22L, "r2");
        article.getUsers().add(r1);
        article.getUsers().add(r2);

        when(userService.getUser(userDetails)).thenReturn(current);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));

        Integer likes = articleService.getCountReactions(articleId, userDetails);
        assertThat(likes).isEqualTo(2);
    }

    @Test
    void getArticleByCategoryId_categoryNotExists_throws() {
        Integer categoryId = 999;
        when(userService.getUser(userDetails)).thenReturn(createUser(13L, "x"));
        when(categoryRepository.existsById(categoryId)).thenReturn(false);

        assertThrows(MyEntityNotFoundException.class, () -> articleService.getArticleByCategoryId(categoryId, userDetails));
    }

    @Test
    void getAllCategories_returnsList() {
        List<CategoryEntity> cats = List.of(createCategory(1, "a"), createCategory(2, "b"));
        when(categoryRepository.findAll()).thenReturn(cats);

        List<CategoryEntity> out = articleService.getAllCategories();
        assertThat(out).isEqualTo(cats);
    }
}
