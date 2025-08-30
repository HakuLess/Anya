package com.haku.anya.ui.reader.animation

import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.view.animation.AnimationSet
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator

object PageTurnAnimation {
    
    fun createNextPageAnimation(): Animation {
        val animationSet = AnimationSet(true)
        
        // 创建位移动画，模拟页面向左滑出
        val translateAnimation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, -1f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f
        )
        translateAnimation.duration = 300
        translateAnimation.interpolator = DecelerateInterpolator()
        
        // 创建透明度动画，模拟页面消失
        val alphaAnimation = AlphaAnimation(1.0f, 0.0f)
        alphaAnimation.duration = 300
        alphaAnimation.interpolator = DecelerateInterpolator()
        
        animationSet.addAnimation(translateAnimation)
        animationSet.addAnimation(alphaAnimation)
        
        return animationSet
    }
    
    fun createPreviousPageAnimation(): Animation {
        val animationSet = AnimationSet(true)
        
        // 创建位移动画，模拟页面向右滑出
        val translateAnimation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f
        )
        translateAnimation.duration = 300
        translateAnimation.interpolator = DecelerateInterpolator()
        
        // 创建透明度动画，模拟页面消失
        val alphaAnimation = AlphaAnimation(1.0f, 0.0f)
        alphaAnimation.duration = 300
        alphaAnimation.interpolator = DecelerateInterpolator()
        
        animationSet.addAnimation(translateAnimation)
        animationSet.addAnimation(alphaAnimation)
        
        return animationSet
    }
    
    fun createPageFlipAnimation(isNext: Boolean): Animation {
        val animationSet = AnimationSet(true)
        
        // 创建3D翻页效果
        val translateAnimation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, if (isNext) -0.5f else 0.5f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f
        )
        translateAnimation.duration = 400
        translateAnimation.interpolator = DecelerateInterpolator()
        
        // 创建缩放动画，模拟3D效果
        val scaleAnimation = android.view.animation.ScaleAnimation(
            1.0f, 0.8f,
            1.0f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnimation.duration = 400
        scaleAnimation.interpolator = DecelerateInterpolator()
        
        // 创建透明度动画
        val alphaAnimation = AlphaAnimation(1.0f, 0.3f)
        alphaAnimation.duration = 400
        alphaAnimation.interpolator = DecelerateInterpolator()
        
        animationSet.addAnimation(translateAnimation)
        animationSet.addAnimation(scaleAnimation)
        animationSet.addAnimation(alphaAnimation)
        
        return animationSet
    }
}
