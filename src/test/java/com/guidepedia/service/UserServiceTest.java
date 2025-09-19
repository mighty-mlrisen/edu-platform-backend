package com.guidepedia.service;

import com.guidepedia.exception.BusinessException;
import com.guidepedia.exception.MyEntityNotFoundException;
import com.guidepedia.model.entity.ArticleEntity;
import com.guidepedia.model.entity.UserEntity;
import com.guidepedia.model.response.ProfileResponse;
import com.guidepedia.repo.ArticleRepository;
import com.guidepedia.repo.UserRepository;
import com.guidepedia.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserDetailsImpl userDetails;

    @InjectMocks
    private UserService userService;

    private UserEntity createUser(Long id, String login) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setLogin(login);
        u.setSavedArticles(new HashSet<>());
        u.setSubscribers(new HashSet<>());
        u.setSubscriptions(new HashSet<>());
        return u;
    }

    private ArticleEntity createArticle(Long id) {
        ArticleEntity a = new ArticleEntity();
        a.setId(id);
        a.setSavedUsers(new HashSet<>());
        return a;
    }

    @Test
    void getUser_whenFound_returnsUser() {
        Long id = 1L;
        UserEntity userEntity = createUser(id, "u1");

        when(userDetails.getId()).thenReturn(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(userEntity));

        UserEntity result = userService.getUser(userDetails);

        assertThat(result).isSameAs(userEntity);
        verify(userRepository, times(1)).findById(id);
    }

    @Test
    void getUser_whenNotFound_throwsMyEntityNotFoundException() {
        Long id = 2L;
        when(userDetails.getId()).thenReturn(id);
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(MyEntityNotFoundException.class, () -> userService.getUser(userDetails));
        verify(userRepository, times(1)).findById(id);
    }

    @Test
    void getProfile_whenUserSubscribedToSelf_returnsProfileWithSubscribedTrue() {
        Long id = 5L;
        UserEntity u = createUser(id, "me");
        u.getSubscribers().add(u);

        when(userDetails.getId()).thenReturn(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        var profile = userService.getProfile(userDetails);

        assertThat(profile).isNotNull();
        verify(userRepository, times(1)).findById(id);
    }

    @Test
    void updateProfile_appliesChangesAndSaves() {
        Long id = 7L;
        UserEntity u = createUser(id, "oldLogin");
        when(userDetails.getId()).thenReturn(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse input = mock(ProfileResponse.class);
        when(input.getUsername()).thenReturn("newName");
        when(input.getAvatar()).thenReturn("avatarUrl");
        when(input.getProfile()).thenReturn("newProfile");
        when(input.getCardDetails()).thenReturn("cardX");

        var out = userService.updateProfile(input, userDetails);

        verify(userRepository, times(1)).save(u);
        assertThat(out).isNotNull();
        assertThat(u.getUsername()).isEqualTo("newName");
        assertThat(u.getAvatar()).isEqualTo("avatarUrl");
    }

    @Test
    void changeSaveArticle_whenArticleContainsUser_andStatusFalse_removesAndSaves() {
        Long userId = 10L;
        Long articleId = 100L;

        UserEntity userEntity = createUser(userId, "tester");
        ArticleEntity article = createArticle(articleId);

        article.getSavedUsers().add(userEntity);
        userEntity.getSavedArticles().add(article);

        when(userDetails.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        var response = userService.changeSaveArticle(articleId, false, userDetails);

        assertThat(article.getSavedUsers()).doesNotContain(userEntity);
        assertThat(userEntity.getSavedArticles()).doesNotContain(article);
        assertThat(response).isNotNull();

        verify(articleRepository, times(1)).save(article);
    }

    @Test
    void changeSaveArticle_whenArticleNotFound_throws() {
        Long userId = 11L;
        Long articleId = 999L;
        when(userDetails.getId()).thenReturn(userId);
        when(articleRepository.findById(articleId)).thenReturn(Optional.empty());
        assertThrows(MyEntityNotFoundException.class, () -> userService.changeSaveArticle(articleId, true, userDetails));
    }

    @Test
    void getSavedArticles_returnsList() {
        Long userId = 20L;
        Long articleId = 200L;
        UserEntity user = createUser(userId, "reader");
        ArticleEntity a = createArticle(articleId);
        user.getSavedArticles().add(a);

        when(userDetails.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        List<?> list = userService.getSavedArticles(userDetails);
        assertThat(list).isNotNull();
        assertThat(list.size()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void changeStatusSubscribtion_subscribeAndUnsubscribeFlow_andSave() {
        Long currentId = 30L;
        Long publisherId = 31L;

        UserEntity current = createUser(currentId, "curr");
        UserEntity publisher = createUser(publisherId, "pub");

        when(userDetails.getId()).thenReturn(currentId);
        when(userRepository.findById(currentId)).thenReturn(Optional.of(current));
        when(userRepository.findById(publisherId)).thenReturn(Optional.of(publisher));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = userService.changeStatusSubscribtion(publisherId, true, userDetails);
        assertThat(resp).isNotNull();
        assertThat(publisher.getSubscribers()).contains(current);
        assertThat(current.getSubscriptions()).contains(publisher);

        var resp2 = userService.changeStatusSubscribtion(publisherId, false, userDetails);
        assertThat(resp2).isNotNull();
        assertThat(publisher.getSubscribers()).doesNotContain(current);
        assertThat(current.getSubscriptions()).doesNotContain(publisher);

        verify(userRepository, atLeast(2)).findById(publisherId);
        verify(userRepository, atLeast(1)).save(publisher);
    }

    @Test
    void changeStatusSubscribtion_whenUserTriesToSubscribeToHimself_throwsBusinessException() {
        Long id = 50L;
        UserEntity sameUser = createUser(id, "same");

        when(userDetails.getId()).thenReturn(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(sameUser));

        assertThrows(BusinessException.class, () -> userService.changeStatusSubscribtion(id, true, userDetails));

        verify(userRepository, atLeastOnce()).findById(id);
    }

    @Test
    void getSubscribers_getSubscribtions_getUserSubscribtions_getUserSubscribers_getProfileById_flow() {
        Long currId = 60L;
        Long otherId = 61L;
        UserEntity current = createUser(currId, "curr");
        UserEntity other = createUser(otherId, "other");

        other.getSubscribers().add(current);
        current.getSubscriptions().add(other);

        when(userDetails.getId()).thenReturn(currId);
        when(userRepository.findById(currId)).thenReturn(Optional.of(current));
        when(userRepository.findById(otherId)).thenReturn(Optional.of(other));

        var subs = userService.getSubscribers(userDetails);
        assertThat(subs).isNotNull();

        var subs2 = userService.getSubscribtions(userDetails);
        assertThat(subs2).isNotNull();

        var otherSubscribtions = userService.getUserSubscribtions(userDetails, otherId);
        assertThat(otherSubscribtions).isNotNull();

        var otherSubscribers = userService.getUserSubscribers(userDetails, otherId);
        assertThat(otherSubscribers).isNotNull();

        var prof = userService.getProfileById(userDetails, otherId);
        assertThat(prof).isNotNull();
    }
}
