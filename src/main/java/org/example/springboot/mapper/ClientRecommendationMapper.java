package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.springboot.entity.QaRecommendQuestion;

import java.util.List;

/**
 * 用户端推荐问题表 Mapper。
 */
@Mapper
public interface ClientRecommendationMapper {

    @Select("""
            SELECT id,
                   question_text,
                   question_type,
                   sort_no,
                   status,
                   created_at,
                   updated_at
            FROM qa_recommend_question
            WHERE status = 1
            ORDER BY sort_no ASC, id ASC
            """)
    @org.apache.ibatis.annotations.Results(value = {
            @org.apache.ibatis.annotations.Result(property = "questionText", column = "question_text"),
            @org.apache.ibatis.annotations.Result(property = "questionType", column = "question_type"),
            @org.apache.ibatis.annotations.Result(property = "sortNo", column = "sort_no"),
            @org.apache.ibatis.annotations.Result(property = "createdAt", column = "created_at"),
            @org.apache.ibatis.annotations.Result(property = "updatedAt", column = "updated_at")
    })
    List<QaRecommendQuestion> findEnabledList();
}
