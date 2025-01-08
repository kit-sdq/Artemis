import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

@Component({
    template: ` <div style="align-content: center;">
        <div style="width: 20%; height: 20%; margin: 0 auto;">
            <div fitText>test</div>
        </div>
    </div>`,
})
class TestFitTextComponent {}

describe('FitTextDirective', () => {
    let fixture: ComponentFixture<TestFitTextComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        fixture = TestBed.createComponent(TestFitTextComponent);
    });

    it('should create an instance', () => {
        expect(fixture).toBeTruthy();
        // TODO: extend test case
    });
});
